package co.edu.corhuila.opti.sales.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Invoice of a work order: one per order, opened together with it. It tracks what has been paid
 * (abonos) and never accepts more than the balance.
 */
public final class Invoice {

    private final UUID id;
    private final String number;
    private final UUID workOrderId;
    private final UUID patientId;
    private final long totalCents;
    private final long paidCents;
    private final InvoiceStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Invoice(UUID id, String number, UUID workOrderId, UUID patientId, long totalCents, long paidCents,
                    InvoiceStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.number = number;
        this.workOrderId = workOrderId;
        this.patientId = patientId;
        this.totalCents = totalCents;
        this.paidCents = paidCents;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Opens the invoice of an order: the total is the order's, nothing paid yet. */
    public static Invoice open(UUID id, String number, WorkOrder order, Instant now) {
        InvoiceStatus status = order.totalCents() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PENDING;
        return new Invoice(id, number, order.id(), order.patientId(), order.totalCents(), 0, status, now, now);
    }

    public static Invoice rehydrate(UUID id, String number, UUID workOrderId, UUID patientId, long totalCents,
                                    long paidCents, InvoiceStatus status, Instant createdAt, Instant updatedAt) {
        return new Invoice(id, number, workOrderId, patientId, totalCents, paidCents, status, createdAt, updatedAt);
    }

    /** Applies a payment; more than the balance, or a payment on a closed invoice, is rejected. */
    public Invoice pay(long amountCents, Instant now) {
        if (status == InvoiceStatus.VOID) {
            throw DomainException.rule("the invoice is void: its order was cancelled");
        }
        if (status == InvoiceStatus.PAID) {
            throw DomainException.rule("the invoice is already paid");
        }
        if (amountCents > balanceCents()) {
            throw DomainException.rule("the payment exceeds the balance of " + balanceCents() + " cents");
        }
        long paid = paidCents + amountCents;
        InvoiceStatus next = paid == totalCents ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL;
        return new Invoice(id, number, workOrderId, patientId, totalCents, paid, next, createdAt, now);
    }

    /** Voids the invoice when its order is cancelled. An invoice with payments cannot be voided. */
    public Invoice voidInvoice(Instant now) {
        if (status == InvoiceStatus.VOID) {
            return this;
        }
        if (paidCents > 0) {
            throw DomainException.rule("an order with payments cannot be cancelled: refund the payments first");
        }
        return new Invoice(id, number, workOrderId, patientId, totalCents, 0, InvoiceStatus.VOID, createdAt, now);
    }

    public long balanceCents() {
        return totalCents - paidCents;
    }

    public boolean hasPayments() {
        return paidCents > 0;
    }

    public UUID id() {
        return id;
    }

    public String number() {
        return number;
    }

    public UUID workOrderId() {
        return workOrderId;
    }

    public UUID patientId() {
        return patientId;
    }

    public long totalCents() {
        return totalCents;
    }

    public long paidCents() {
        return paidCents;
    }

    public InvoiceStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
