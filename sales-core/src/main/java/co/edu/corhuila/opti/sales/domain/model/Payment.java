package co.edu.corhuila.opti.sales.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A payment (abono) received against an invoice. Never modified once recorded. */
public record Payment(UUID id, UUID invoiceId, long amountCents, PaymentMethod method, String reference,
                      String gatewayTransactionId, Instant paidAt) {

    private static final long MAX_CENTS = 1_000_000_000_000L;

    /** Raw input of a payment, before validation. */
    public record Data(Long amountCents, PaymentMethod method, String reference) {
    }

    /** Input that passed every rule; an electronic method still needs the gateway's say before it becomes a Payment. */
    public record Checked(long amountCents, PaymentMethod method, String reference) {
    }

    public static Checked check(Data data) {
        Violations v = new Violations();
        Long amount = v.check(() -> amount(data.amountCents()));
        PaymentMethod method = v.check(() -> Validation.required(data.method(), "method"));
        String reference = v.check(() -> Validation.optionalText(data.reference(), "reference", 100));
        v.throwIfAny();
        return new Checked(amount, method, reference);
    }

    /** {@code gatewayTransactionId} is null for a manually recorded method (CASH, TRANSFER, OTHER). */
    public static Payment register(UUID id, UUID invoiceId, Checked checked, String gatewayTransactionId,
                                   Instant now) {
        return new Payment(id, invoiceId, checked.amountCents(), checked.method(), checked.reference(),
                gatewayTransactionId, now);
    }

    private static long amount(Long value) {
        Validation.required(value, "amountCents");
        if (value < 1 || value > MAX_CENTS) {
            throw DomainException.validation("amountCents", "must be between 1 and " + MAX_CENTS + " cents");
        }
        return value;
    }
}
