package co.edu.corhuila.opti.sales.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.domain.model.Invoice;

/** Persistence of invoices. */
public interface InvoiceRepository {

    void insert(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    /** Locks the invoice row so two payments cannot both fit in the same balance. */
    Optional<Invoice> findByIdForUpdate(UUID id);

    Optional<Invoice> findByWorkOrderIdForUpdate(UUID workOrderId);

    PageResult<Invoice> search(InvoiceFilter filter, PageQuery page);

    /** Persists the mutable part of the invoice: paid amount, status and update time. */
    void update(Invoice invoice);
}
