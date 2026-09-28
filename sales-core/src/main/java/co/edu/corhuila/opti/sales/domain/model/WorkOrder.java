package co.edu.corhuila.opti.sales.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Work order aggregate root. Pure domain object.
 * Created as a QUOTATION; the total is always the sum of its lines, never a client-supplied figure.
 */
public final class WorkOrder {

    private static final int MAX_ITEMS = 20;
    private static final long MAX_TOTAL_CENTS = 1_000_000_000_000L;

    private final UUID id;
    private final String number;
    private final UUID patientId;
    private final String reference;
    private final WorkOrderStatus status;
    private final List<WorkOrderItem> items;
    private final long totalCents;
    private final Instant createdAt;
    private final Instant updatedAt;

    private WorkOrder(UUID id, String number, UUID patientId, String reference, WorkOrderStatus status,
                      List<WorkOrderItem> items, long totalCents, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.number = number;
        this.patientId = patientId;
        this.reference = reference;
        this.status = status;
        this.items = List.copyOf(items);
        this.totalCents = totalCents;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Input that passed every rule, ready to become an order. */
    public record Checked(UUID patientId, String reference, List<WorkOrderItem> items, long totalCents) {
    }

    /** Validates the raw input. Every broken rule is reported, including the ones of each line. */
    public static Checked check(Data data, Supplier<UUID> itemIds) {
        Violations v = new Violations();
        UUID patient = v.check(() -> Validation.required(data.patientId(), "patientId"));
        String reference = v.check(() -> Validation.text(data.reference(), "reference", 1, 64));
        List<WorkOrderItem> items = v.check(() -> lines(data.items(), itemIds));
        v.throwIfAny();
        long total = items.stream().mapToLong(WorkOrderItem::subtotalCents).sum();
        if (total > MAX_TOTAL_CENTS) {
            throw DomainException.validation("items", "the total exceeds " + MAX_TOTAL_CENTS + " cents");
        }
        return new Checked(patient, reference, items, total);
    }

    /** Opens a quotation from validated input. The number is requested only now, so rejected input burns none. */
    public static WorkOrder open(UUID id, Supplier<String> number, Checked input, Instant now) {
        return new WorkOrder(id, number.get(), input.patientId(), input.reference(), WorkOrderStatus.QUOTATION,
                input.items(), input.totalCents(), now, now);
    }

    public static WorkOrder rehydrate(UUID id, String number, UUID patientId, String reference,
                                      WorkOrderStatus status, List<WorkOrderItem> items, long totalCents,
                                      Instant createdAt, Instant updatedAt) {
        return new WorkOrder(id, number, patientId, reference, status, items, totalCents, createdAt, updatedAt);
    }

    /** A quotation is approved once; approving again is an invalid transition. */
    public WorkOrder approve(Instant now) {
        if (status != WorkOrderStatus.QUOTATION) {
            throw DomainException.transition("only a quotation can be approved, the order is " + status);
        }
        return withStatus(WorkOrderStatus.APPROVED, now);
    }

    /** Moves an approved order along APPROVED, IN_LABORATORY, READY, DELIVERED. */
    public WorkOrder advance(Instant now) {
        WorkOrderStatus next = status.next().orElseThrow(() ->
                DomainException.transition("the order cannot advance from " + status));
        return withStatus(next, now);
    }

    /** Cancelling twice is harmless (compensations repeat); a delivered or in-laboratory order cannot be cancelled. */
    public WorkOrder cancel(Instant now) {
        if (status == WorkOrderStatus.CANCELLED) {
            return this;
        }
        if (!status.canBeCancelled()) {
            throw DomainException.transition("an order that is " + status + " cannot be cancelled");
        }
        return withStatus(WorkOrderStatus.CANCELLED, now);
    }

    public boolean isCancelled() {
        return status == WorkOrderStatus.CANCELLED;
    }

    private WorkOrder withStatus(WorkOrderStatus next, Instant now) {
        return new WorkOrder(id, number, patientId, reference, next, items, totalCents, createdAt, now);
    }

    private static List<WorkOrderItem> lines(List<WorkOrderItem.Data> data, Supplier<UUID> itemIds) {
        if (data == null || data.isEmpty() || data.size() > MAX_ITEMS) {
            throw DomainException.validation("items", "must have between 1 and " + MAX_ITEMS + " lines");
        }
        Violations v = new Violations();
        List<WorkOrderItem> lines = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            String prefix = "items[" + i + "]";
            WorkOrderItem.Data line = data.get(i);
            WorkOrderItem item = v.check(() -> WorkOrderItem.of(itemIds.get(), Validation.required(line, prefix), prefix));
            if (item != null) {
                lines.add(item);
            }
        }
        v.throwIfAny();
        return lines;
    }

    public UUID id() {
        return id;
    }

    public String number() {
        return number;
    }

    public UUID patientId() {
        return patientId;
    }

    public String reference() {
        return reference;
    }

    public WorkOrderStatus status() {
        return status;
    }

    public List<WorkOrderItem> items() {
        return items;
    }

    public long totalCents() {
        return totalCents;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Raw input to open a work order, before validation. */
    public record Data(UUID patientId, String reference, List<WorkOrderItem.Data> items) {
    }
}
