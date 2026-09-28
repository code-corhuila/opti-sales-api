package co.edu.corhuila.opti.sales.domain.model;

/** Payment state of an invoice; VOID when the order was cancelled before any payment. */
public enum InvoiceStatus {
    PENDING,
    PARTIAL,
    PAID,
    VOID
}
