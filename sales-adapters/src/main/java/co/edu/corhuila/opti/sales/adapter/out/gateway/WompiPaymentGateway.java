package co.edu.corhuila.opti.sales.adapter.out.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/**
 * Real integration with Wompi (wompi.co), Colombia's no-subscription gateway: nothing charged
 * until a payment is actually approved, then a percentage fee - no monthly cost, unlike most
 * alternatives.
 *
 * <p>Only NEQUI is wired here: a Nequi charge needs just the customer's phone number, which
 * already fits the existing {@code reference} field - no new card-capture UI (and its PCI
 * handling) was in scope. CARD/PSE/DAVIPLATA stay on the local {@link SandboxPaymentGateway}
 * until that UI exists; see {@link CompositePaymentGateway}.
 *
 * <p>Sandbox keys are free: create a merchant account at https://comercios.wompi.co, switch it to
 * Sandbox, and copy WOMPI_PUBLIC_KEY / WOMPI_PRIVATE_KEY / WOMPI_INTEGRITY_SECRET
 * (pub_test_.../prv_test_.../test_integrity_...) into the environment. Wompi's own published
 * sandbox numbers: 3991111111 always approves, 3992222222 always declines - use either as the
 * payment reference to test both outcomes without a real phone.
 *
 * <p>A Nequi charge is asynchronous on Wompi's side (the customer approves a push notification in
 * their app, then the transaction itself settles): this polls both steps with a short, bounded
 * wait, which is what the sandbox test numbers are made for - in production a real customer may
 * take longer than this gateway is willing to block an HTTP request for, a known limitation of
 * keeping the existing synchronous PaymentGateway contract instead of a webhook-driven redesign.
 */
public class WompiPaymentGateway implements PaymentGateway {

    private static final Pattern CO_PHONE = Pattern.compile("^3\\d{9}$");
    private static final String CURRENCY = "COP";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    /** Bounded so both polling phases together stay under the gateway's 30s proxy_read_timeout. */
    private static final int MAX_POLLS = 6;

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String publicKey;
    private final String privateKey;
    private final String integritySecret;

    public WompiPaymentGateway(HttpClient http, ObjectMapper json, String baseUrl, String publicKey,
                               String privateKey, String integritySecret) {
        this.http = http;
        this.json = json;
        this.baseUrl = baseUrl;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.integritySecret = integritySecret;
    }

    @Override
    public GatewayResult authorize(PaymentMethod method, long amountCents, String reference) {
        if (method != PaymentMethod.NEQUI) {
            throw new IllegalArgumentException("WompiPaymentGateway only handles NEQUI");
        }
        String phone = reference == null ? "" : reference.replaceAll("[^0-9]", "");
        if (!CO_PHONE.matcher(phone).matches()) {
            return new GatewayResult(false, null, "the Nequi phone number is not valid (must be 10 digits starting with 3)");
        }
        try {
            String acceptanceToken = acceptanceToken();
            String nequiToken = nequiToken(phone);
            if (!waitForNequiApproval(nequiToken)) {
                return new GatewayResult(false, null, "the customer did not approve the charge in the Nequi app in time");
            }
            String transactionId = createTransaction(amountCents, phone, acceptanceToken, nequiToken);
            return waitForTransaction(transactionId);
        } catch (WompiCallException e) {
            return new GatewayResult(false, null, "the payment gateway did not respond: " + e.getMessage());
        }
    }

    private String acceptanceToken() {
        JsonNode body = get(baseUrl + "/merchants/" + publicKey, publicKey);
        return body.path("data").path("presigned_acceptance").path("acceptance_token").asText();
    }

    private String nequiToken(String phone) {
        JsonNode body = post(baseUrl + "/tokens/nequi", publicKey, Map.of("phone_number", phone));
        return body.path("data").path("id").asText();
    }

    /** True once the customer approves the push notification in their Nequi app ({@code APPROVED}). */
    private boolean waitForNequiApproval(String nequiToken) {
        for (int i = 0; i < MAX_POLLS; i++) {
            JsonNode body = get(baseUrl + "/tokens/nequi/" + nequiToken, publicKey);
            String status = body.path("data").path("status").asText();
            if ("APPROVED".equals(status)) {
                return true;
            }
            if (!"PENDING".equals(status)) {
                return false;
            }
            sleep();
        }
        return false;
    }

    private String createTransaction(long amountCents, String phone, String acceptanceToken, String nequiToken) {
        String txReference = "opti-" + phone + "-" + System.currentTimeMillis();
        Map<String, Object> paymentMethod = Map.of("type", "NEQUI", "phone_number", phone, "token", nequiToken);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount_in_cents", amountCents);
        request.put("currency", CURRENCY);
        request.put("customer_email", "cliente+" + phone + "@opticaview.test");
        request.put("reference", txReference);
        request.put("acceptance_token", acceptanceToken);
        request.put("payment_method", paymentMethod);
        request.put("signature", sign(txReference, amountCents));
        JsonNode body = post(baseUrl + "/transactions", privateKey, request);
        return body.path("data").path("id").asText();
    }

    /** True settles into APPROVED/DECLINED/ERROR/VOIDED; a fresh transaction starts PENDING. */
    private GatewayResult waitForTransaction(String transactionId) {
        for (int i = 0; i < MAX_POLLS; i++) {
            JsonNode body = get(baseUrl + "/transactions/" + transactionId, privateKey);
            String status = body.path("data").path("status").asText();
            if ("APPROVED".equals(status)) {
                return new GatewayResult(true, transactionId, null);
            }
            if (!"PENDING".equals(status)) {
                return new GatewayResult(false, null, "the gateway " + status.toLowerCase() + " the payment");
            }
            sleep();
        }
        return new GatewayResult(false, null, "the gateway did not confirm the payment in time");
    }

    /** SHA-256(reference + amount_in_cents + currency + integrity secret), hex-encoded (Wompi's integrity signature). */
    private String sign(String reference, long amountCents) {
        try {
            String raw = reference + amountCents + CURRENCY + integritySecret;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WompiCallException("interrupted while waiting for the gateway");
        }
    }

    private JsonNode get(String url, String bearer) {
        return send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + bearer).GET().build());
    }

    private JsonNode post(String url, String bearer, Object body) {
        try {
            String payload = json.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            return send(request);
        } catch (JsonProcessingException e) {
            throw new WompiCallException("could not serialize the request: " + e.getMessage());
        }
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new WompiCallException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new WompiCallException("network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WompiCallException("interrupted");
        }
    }

    private static final class WompiCallException extends RuntimeException {
        WompiCallException(String message) {
            super(message);
        }
    }
}
