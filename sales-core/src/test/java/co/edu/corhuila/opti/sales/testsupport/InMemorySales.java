package co.edu.corhuila.opti.sales.testsupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.SalesSummary;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases.WorkOrderFilter;
import co.edu.corhuila.opti.sales.application.port.out.InvoiceRepository;
import co.edu.corhuila.opti.sales.application.port.out.NumberSequence;
import co.edu.corhuila.opti.sales.application.port.out.PaymentRepository;
import co.edu.corhuila.opti.sales.application.port.out.SalesReportRepository;
import co.edu.corhuila.opti.sales.application.port.out.WorkOrderRepository;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/** In-memory fakes of the sales stores, used to test the core and the HTTP adapter without a database. */
public final class InMemorySales {

    private InMemorySales() {
    }

    private static <T> PageResult<T> page(List<T> sorted, PageQuery page) {
        int from = Math.min(page.offset(), sorted.size());
        int to = Math.min(from + page.limit(), sorted.size());
        return new PageResult<>(sorted.subList(from, to), page.page(), page.limit(), sorted.size());
    }

    /** Work orders. */
    public static class Orders implements WorkOrderRepository {

        private final Map<UUID, WorkOrder> byId = new HashMap<>();

        @Override
        public void insert(WorkOrder order) {
            byId.put(order.id(), order);
        }

        @Override
        public Optional<WorkOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<WorkOrder> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page) {
            List<WorkOrder> matches = byId.values().stream()
                    .filter(o -> filter.status() == null || o.status() == filter.status())
                    .filter(o -> filter.patientId() == null || o.patientId().equals(filter.patientId()))
                    .filter(o -> filter.createdBefore() == null || o.createdAt().isBefore(filter.createdBefore()))
                    .sorted(Comparator.comparing(WorkOrder::createdAt).reversed().thenComparing(WorkOrder::id))
                    .toList();
            return page(matches, page);
        }

        @Override
        public void update(WorkOrder order) {
            byId.put(order.id(), order);
        }

        List<WorkOrder> all() {
            return List.copyOf(byId.values());
        }
    }

    /** Revenue reports, aggregated over the same orders a test already opened. */
    public static class Reports implements SalesReportRepository {

        private final Orders orders;

        public Reports(Orders orders) {
            this.orders = orders;
        }

        @Override
        public SalesSummary aggregate(UUID sellerId, java.time.Instant from, java.time.Instant to) {
            List<WorkOrder> matches = orders.all().stream()
                    .filter(o -> o.status() != WorkOrderStatus.CANCELLED)
                    .filter(o -> sellerId == null || sellerId.equals(o.sellerId()))
                    .filter(o -> from == null || !o.createdAt().isBefore(from))
                    .filter(o -> to == null || o.createdAt().isBefore(to))
                    .toList();
            return new SalesSummary(matches.size(), matches.stream().mapToLong(WorkOrder::totalCents).sum());
        }
    }

    /** Invoices. */
    public static class Invoices implements InvoiceRepository {

        private final Map<UUID, Invoice> byId = new HashMap<>();

        @Override
        public void insert(Invoice invoice) {
            byId.put(invoice.id(), invoice);
        }

        @Override
        public Optional<Invoice> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Invoice> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<Invoice> findByWorkOrderIdForUpdate(UUID workOrderId) {
            return byId.values().stream().filter(i -> i.workOrderId().equals(workOrderId)).findFirst();
        }

        @Override
        public PageResult<Invoice> search(InvoiceFilter filter, PageQuery page) {
            List<Invoice> matches = byId.values().stream()
                    .filter(i -> filter.status() == null || i.status() == filter.status())
                    .filter(i -> filter.workOrderId() == null || i.workOrderId().equals(filter.workOrderId()))
                    .sorted(Comparator.comparing(Invoice::createdAt).reversed().thenComparing(Invoice::id))
                    .toList();
            return page(matches, page);
        }

        @Override
        public void update(Invoice invoice) {
            byId.put(invoice.id(), invoice);
        }
    }

    /** Payments. */
    public static class Payments implements PaymentRepository {

        private final List<Payment> all = new ArrayList<>();

        @Override
        public void insert(Payment payment) {
            all.add(payment);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return all.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public PageResult<Payment> list(UUID invoiceId, PageQuery page) {
            List<Payment> mine = all.stream().filter(p -> p.invoiceId().equals(invoiceId))
                    .sorted(Comparator.comparing(Payment::paidAt).reversed()).toList();
            return page(mine, page);
        }
    }

    /** Sequential OT-0001 / FV-0001 numbers. */
    public static class Numbers implements NumberSequence {

        private final AtomicInteger orders = new AtomicInteger();
        private final AtomicInteger invoices = new AtomicInteger();

        @Override
        public String nextWorkOrderNumber() {
            return "OT-%04d".formatted(orders.incrementAndGet());
        }

        @Override
        public String nextInvoiceNumber() {
            return "FV-%04d".formatted(invoices.incrementAndGet());
        }
    }
}
