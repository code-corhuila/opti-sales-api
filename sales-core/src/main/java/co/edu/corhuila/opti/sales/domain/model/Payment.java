package co.edu.corhuila.opti.sales.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A payment (abono) received against an invoice. Never modified once recorded. */
public record Payment(UUID id, UUID invoiceId, long amountCents, PaymentMethod method, String reference,
                      Instant paidAt) {

    private static final long MAX_CENTS = 1_000_000_000_000L;

    /** Raw input of a payment, before validation. */
    public record Data(Long amountCents, PaymentMethod method, String reference) {
    }

    public static Payment register(UUID id, UUID invoiceId, Data data, Instant now) {
        Violations v = new Violations();
        Long amount = v.check(() -> amount(data.amountCents()));
        PaymentMethod method = v.check(() -> Validation.required(data.method(), "method"));
        String reference = v.check(() -> Validation.optionalText(data.reference(), "reference", 100));
        v.throwIfAny();
        return new Payment(id, invoiceId, amount, method, reference, now);
    }

    private static long amount(Long value) {
        Validation.required(value, "amountCents");
        if (value < 1 || value > MAX_CENTS) {
            throw DomainException.validation("amountCents", "must be between 1 and " + MAX_CENTS + " cents");
        }
        return value;
    }
}
