package co.edu.corhuila.opti.sales.application.port.out;

import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.domain.model.Payment;

/** Persistence of payments (append-only). */
public interface PaymentRepository {

    void insert(Payment payment);

    Optional<Payment> findById(UUID id);

    PageResult<Payment> list(UUID invoiceId, PageQuery page);
}
