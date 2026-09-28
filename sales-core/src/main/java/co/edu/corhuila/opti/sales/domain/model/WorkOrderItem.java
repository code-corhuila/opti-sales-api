package co.edu.corhuila.opti.sales.domain.model;

import java.util.UUID;
import java.util.regex.Pattern;

/** One line of a work order, priced by the products domain when the stock was reserved. */
public record WorkOrderItem(UUID id, UUID frameId, UUID reservationId, String sku, String description, int quantity,
                            long unitPriceCents, long subtotalCents) {

    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,59}$");
    static final long MAX_CENTS = 100_000_000_000L;
    private static final int MAX_QUANTITY = 100;

    /** Raw input of a line, before validation. */
    public record Data(UUID frameId, UUID reservationId, String sku, String description, Integer quantity,
                       Long unitPriceCents) {
    }

    /** Validates a line; {@code prefix} (for example {@code items[0]}) names the field in each error. */
    static WorkOrderItem of(UUID id, Data d, String prefix) {
        Violations v = new Violations();
        UUID frame = v.check(() -> Validation.required(d.frameId(), prefix + ".frameId"));
        UUID reservation = v.check(() -> Validation.required(d.reservationId(), prefix + ".reservationId"));
        String sku = v.check(() -> Validation.matching(d.sku(), prefix + ".sku", SKU, "is not a valid sku"));
        String description = v.check(() -> Validation.text(d.description(), prefix + ".description", 1, 150));
        Integer quantity = v.check(() -> Validation.intBetween(
                Validation.required(d.quantity(), prefix + ".quantity"), prefix + ".quantity", 1, MAX_QUANTITY));
        Long price = v.check(() -> cents(d.unitPriceCents(), prefix + ".unitPriceCents"));
        v.throwIfAny();
        return new WorkOrderItem(id, frame, reservation, sku, description, quantity, price,
                Math.multiplyExact((long) quantity, price));
    }

    static long cents(Long value, String field) {
        Validation.required(value, field);
        if (value < 0 || value > MAX_CENTS) {
            throw DomainException.validation(field, "must be between 0 and " + MAX_CENTS + " cents");
        }
        return value;
    }
}
