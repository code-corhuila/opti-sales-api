package co.edu.corhuila.opti.sales.application.port.in;

import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.Created;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.InvoiceStatus;
import co.edu.corhuila.opti.sales.domain.model.Payment;

/** What the sales service offers about invoices and their payments. */
public interface InvoiceUseCases {

    Invoice get(UUID id);

    PageResult<Invoice> search(InvoiceFilter filter, PageQuery page);

    /** Records an abono; the invoice becomes PARTIAL, then PAID when the balance reaches zero. */
    Created<Payment> pay(UUID invoiceId, Payment.Data data, String idempotencyKey);

    PageResult<Payment> payments(UUID invoiceId, PageQuery page);

    /** Listing criteria; every field is optional. */
    record InvoiceFilter(InvoiceStatus status, UUID workOrderId) {
    }
}
