package co.edu.corhuila.opti.sales.application.usecase;

import java.time.Clock;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.out.Created;
import co.edu.corhuila.opti.sales.application.port.out.IdGenerator;
import co.edu.corhuila.opti.sales.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.sales.application.port.out.InvoiceRepository;
import co.edu.corhuila.opti.sales.application.port.out.PaymentRepository;
import co.edu.corhuila.opti.sales.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.Validation;
import co.edu.corhuila.opti.sales.domain.model.Violations;

/** Invoices and payments. A payment locks the invoice row, so the balance is never overspent. */
public class InvoiceService implements InvoiceUseCases {

    private static final String PAYMENT = "PAYMENT";

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, IdempotencyStore keys,
                          IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Invoice get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> DomainException.notFound("invoice not found"));
    }

    @Override
    public PageResult<Invoice> search(InvoiceFilter filter, PageQuery page) {
        return invoices.search(filter, page);
    }

    @Override
    public Created<Payment> pay(UUID invoiceId, Payment.Data data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        Payment payment = v.check(() -> Payment.register(ids.next(), invoiceId, data, clock.instant()));
        v.throwIfAny();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, PAYMENT, payment.id())) {
                UUID existing = keys.find(key, PAYMENT).orElseThrow();
                return new Created<>(payments.findById(existing).orElseThrow(), false);
            }
            Invoice invoice = invoices.findByIdForUpdate(invoiceId)
                    .orElseThrow(() -> DomainException.notFound("invoice not found"));
            invoices.update(invoice.pay(payment.amountCents(), clock.instant()));
            payments.insert(payment);
            return new Created<>(payment, true);
        });
    }

    @Override
    public PageResult<Payment> payments(UUID invoiceId, PageQuery page) {
        get(invoiceId);
        return payments.list(invoiceId, page);
    }
}
