package co.edu.corhuila.opti.sales.adapter.in.http;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import co.edu.corhuila.opti.sales.application.port.out.Created;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** Local educational simulator. Never accepts phone numbers, card details or contacts a provider.
 * Challenges are process-local, bounded and fail closed after restart; receipts are durable via Store.
 */
public final class PaymentGuard {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public record Receipt(String contentType, String base64) { }
    public record Document(String contentType, byte[] bytes) { }
    public record Challenge(UUID id, String testCode, Instant expiresAt, String mode) { }
    public interface Store {
        <T> T transaction(Supplier<T> work);
        void save(UUID paymentId, UUID invoiceId, Document document);
        Optional<Document> get(UUID paymentId, UUID invoiceId);
        boolean exists(UUID paymentId, UUID invoiceId);
    }
    private static final class Pending {
        String subject; UUID invoice; Payment.Data data; byte[] digest; Instant expires;
        int attempts; String key; Created<Payment> result;
    }
    private final Clock clock;
    private final Store store;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Pending> challenges = new HashMap<>();
    public PaymentGuard(Clock clock, Store store) { this.clock = clock; this.store = store; }

    public synchronized Challenge start(String subject, UUID invoice, Payment.Data data) {
        Payment.check(data);
        if (!otpMethod(data.method())) throw ApiException.validation("method", "Elige PSE o tarjeta para el simulador.");
        challenges.entrySet().removeIf(e -> e.getValue().expires.plusSeconds(600).isBefore(clock.instant()));
        if (challenges.size() >= 1000) throw ApiException.validation("otp", "Simulador ocupado; intenta más tarde.");
        // At most one active challenge per caller and invoice; restarting cannot reset the attempt limit immediately.
        for (Pending previous : challenges.values()) {
            if (previous.subject.equals(subject) && previous.invoice.equals(invoice)
                && previous.expires.isAfter(clock.instant()) && previous.result == null)
                throw ApiException.validation("otp", "Ya existe un código para esta factura. Espera su caducidad (3 minutos).");
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        UUID id = UUID.randomUUID();
        Pending p = new Pending(); p.subject = subject; p.invoice = invoice; p.data = data;
        p.digest = hash(id + ":" + code); p.expires = clock.instant().plusSeconds(180);
        challenges.put(id, p);
        return new Challenge(id, code, p.expires, "SMS SIMULADO - SOLO PRUEBAS, SIN COBRO");
    }

    public synchronized Created<Payment> pay(String subject, UUID invoice, Payment.Data data, String key,
            UUID challengeId, String code, Receipt receipt, Supplier<Created<Payment>> action) {
        co.edu.corhuila.opti.sales.domain.model.Validation.idempotencyKey(key);
        Payment.check(data);
        Document document = receipt == null ? null : validate(receipt);
        if (requiresReceipt(data.method()) && document == null)
            throw ApiException.validation("receipt", "Adjunta un comprobante PDF, PNG o JPEG de hasta 2 MiB.");
        Pending p = null;
        if (otpMethod(data.method())) {
            p = challenges.get(challengeId);
            if (p == null || !p.subject.equals(subject) || !p.invoice.equals(invoice) || !p.data.equals(data))
                throw ApiException.validation("otp", "El código no corresponde a esta operación.");
            // A transport retry with the same key returns the original result, never another payment.
            if (p.result != null) {
                if (Objects.equals(p.key, key)) return new Created<>(p.result.value(), false);
                throw ApiException.validation("otp", "Este código ya fue utilizado.");
            }
            if (!p.expires.isAfter(clock.instant())) throw ApiException.validation("otp", "El código caducó. Solicita uno nuevo.");
            if (p.attempts >= 3) throw ApiException.validation("otp", "Se agotaron los 3 intentos. Espera la caducidad del código.");
            p.attempts++;
            if (code == null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(p.digest, hash(challengeId + ":" + code)))
                throw ApiException.validation("otp", "Código incorrecto. Quedan " + (3 - p.attempts) + " intentos.");
        }
        Created<Payment> result = store.transaction(() -> {
            Created<Payment> paid = action.get();
            if (document != null && paid.created()) store.save(paid.value().id(), invoice, document);
            return paid;
        });
        if (p != null) { p.key = key; p.result = result; }
        return result;
    }
    public boolean hasReceipt(UUID paymentId, UUID invoiceId) { return store.exists(paymentId, invoiceId); }
    public Document receipt(UUID paymentId, UUID invoiceId) {
        return store.get(paymentId, invoiceId).orElseThrow(() ->
            co.edu.corhuila.opti.sales.domain.model.DomainException.notFound("No se encontró el comprobante"));
    }
    public static boolean otpMethod(PaymentMethod method) { return method == PaymentMethod.CARD || method == PaymentMethod.PSE; }
    public static boolean requiresReceipt(PaymentMethod method) {
        return method == PaymentMethod.TRANSFER || method == PaymentMethod.NEQUI || method == PaymentMethod.DAVIPLATA;
    }
    public static Document validate(Receipt receipt) {
        if (receipt.base64() == null || receipt.base64().length() > ((MAX_BYTES + 2) / 3) * 4)
            throw ApiException.validation("receipt", "El comprobante supera 2 MiB o está vacío.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(receipt.base64()); }
        catch (IllegalArgumentException e) { throw ApiException.validation("receipt", "El archivo adjunto no es válido."); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw ApiException.validation("receipt", "El comprobante debe pesar entre 1 byte y 2 MiB.");
        boolean valid = switch (Objects.toString(receipt.contentType(), "")) {
            case "application/pdf" -> starts(bytes, new byte[]{37,80,68,70,45});
            case "image/png" -> starts(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "image/jpeg" -> starts(bytes, new byte[]{(byte)255,(byte)216,(byte)255});
            default -> false;
        };
        if (!valid) throw ApiException.validation("receipt", "Solo se permiten archivos PDF, PNG y JPEG cuyo contenido coincida con el tipo.");
        return new Document(receipt.contentType(), bytes);
    }
    private static boolean starts(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }
    private static byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
