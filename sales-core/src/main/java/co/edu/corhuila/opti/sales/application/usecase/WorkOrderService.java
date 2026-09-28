package co.edu.corhuila.opti.sales.application.usecase;

import java.time.Clock;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.application.port.out.Created;
import co.edu.corhuila.opti.sales.application.port.out.IdGenerator;
import co.edu.corhuila.opti.sales.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.sales.application.port.out.InvoiceRepository;
import co.edu.corhuila.opti.sales.application.port.out.NumberSequence;
import co.edu.corhuila.opti.sales.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.sales.application.port.out.WorkOrderRepository;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.Validation;
import co.edu.corhuila.opti.sales.domain.model.Violations;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;

/**
 * Work order lifecycle. Opening claims the idempotency key first, then creates the order and its
 * invoice together; every transition locks the order row so concurrent requests cannot both win.
 */
public class WorkOrderService implements WorkOrderUseCases {

    private static final String WORK_ORDER = "WORK_ORDER";

    private final WorkOrderRepository orders;
    private final InvoiceRepository invoices;
    private final NumberSequence numbers;
    private final IdempotencyStore keys;
    private final IdGenerator ids;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository orders, InvoiceRepository invoices, NumberSequence numbers,
                            IdempotencyStore keys, IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        this.orders = orders;
        this.invoices = invoices;
        this.numbers = numbers;
        this.keys = keys;
        this.ids = ids;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Created<WorkOrder> open(WorkOrder.Data data, String idempotencyKey) {
        Violations v = new Violations();
        String key = v.check(() -> Validation.idempotencyKey(idempotencyKey));
        WorkOrder.Checked input = v.check(() -> WorkOrder.check(data, ids::next));
        v.throwIfAny();
        UUID orderId = ids.next();
        return unitOfWork.run(() -> {
            if (!keys.claim(key, WORK_ORDER, orderId)) {
                return new Created<>(get(keys.find(key, WORK_ORDER).orElseThrow()), false);
            }
            WorkOrder order = WorkOrder.open(orderId, numbers::nextWorkOrderNumber, input, clock.instant());
            orders.insert(order);
            invoices.insert(Invoice.open(ids.next(), numbers.nextInvoiceNumber(), order, clock.instant()));
            return new Created<>(order, true);
        });
    }

    @Override
    public WorkOrder get(UUID id) {
        return orders.findById(id).orElseThrow(() -> DomainException.notFound("work order not found"));
    }

    @Override
    public PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page) {
        return orders.search(filter, page);
    }

    @Override
    public WorkOrder approve(UUID id) {
        return unitOfWork.run(() -> {
            WorkOrder approved = locked(id).approve(clock.instant());
            orders.update(approved);
            return approved;
        });
    }

    @Override
    public WorkOrder advance(UUID id) {
        return unitOfWork.run(() -> {
            WorkOrder advanced = locked(id).advance(clock.instant());
            orders.update(advanced);
            return advanced;
        });
    }

    @Override
    public WorkOrder cancel(UUID id) {
        return unitOfWork.run(() -> {
            WorkOrder order = locked(id);
            if (order.isCancelled()) {
                return order;
            }
            WorkOrder cancelled = order.cancel(clock.instant());
            Invoice voided = invoices.findByWorkOrderIdForUpdate(id)
                    .orElseThrow(() -> DomainException.rule("the order has no invoice"))
                    .voidInvoice(clock.instant());
            orders.update(cancelled);
            invoices.update(voided);
            return cancelled;
        });
    }

    private WorkOrder locked(UUID id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("work order not found"));
    }
}
