package co.edu.corhuila.opti.sales.domain.model;

import java.util.Optional;

/**
 * Lifecycle of a work order. The happy path is linear; cancelling is possible only before the
 * order reaches the laboratory.
 */
public enum WorkOrderStatus {
    QUOTATION,
    APPROVED,
    IN_LABORATORY,
    READY,
    DELIVERED,
    CANCELLED;

    /** The status that follows in the happy path after approval (APPROVED to DELIVERED). */
    public Optional<WorkOrderStatus> next() {
        return switch (this) {
            case APPROVED -> Optional.of(IN_LABORATORY);
            case IN_LABORATORY -> Optional.of(READY);
            case READY -> Optional.of(DELIVERED);
            default -> Optional.empty();
        };
    }

    public boolean canBeCancelled() {
        return this == QUOTATION || this == APPROVED;
    }
}
