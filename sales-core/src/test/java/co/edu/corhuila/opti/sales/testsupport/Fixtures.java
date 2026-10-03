package co.edu.corhuila.opti.sales.testsupport;

import java.util.List;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.application.usecase.InvoiceService;
import co.edu.corhuila.opti.sales.application.usecase.ReportService;
import co.edu.corhuila.opti.sales.application.usecase.WorkOrderService;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderItem;

/** Ready-made valid inputs and a service pair wired over the same in-memory stores. */
public final class Fixtures {

    public static final String START = "2026-09-29T15:00:00Z";
    public static final UUID PATIENT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private Fixtures() {
    }

    /** All three services share the stores, like in production they share the database. */
    public record Sales(WorkOrderUseCases orders, InvoiceUseCases invoices, ReportUseCases reports) {
    }

    public static Sales sales(TestClock clock) {
        return sales(clock, AlwaysApprovePaymentGateway.INSTANCE);
    }

    public static Sales sales(TestClock clock, PaymentGateway gateway) {
        var orders = new InMemorySales.Orders();
        var invoices = new InMemorySales.Invoices();
        var payments = new InMemorySales.Payments();
        var keys = new InMemoryIdempotencyStore();
        var ids = new SequentialIds();
        var unit = new DirectUnitOfWork(keys);
        return new Sales(
                new WorkOrderService(orders, invoices, new InMemorySales.Numbers(), keys, ids, unit, clock),
                new InvoiceService(invoices, payments, gateway, keys, ids, unit, clock),
                new ReportService(new InMemorySales.Reports(orders)));
    }

    /** One frame line: 2 units at 52,000,000 cents = 104,000,000 cents. */
    public static WorkOrder.Data validOrder() {
        return order("saga-1", line("RB5228-2000", 2, 52_000_000L));
    }

    public static WorkOrder.Data order(String reference, WorkOrderItem.Data... lines) {
        return new WorkOrder.Data(PATIENT, reference, List.of(lines));
    }

    public static WorkOrderItem.Data line(String sku, int quantity, long unitPriceCents) {
        return new WorkOrderItem.Data(UUID.randomUUID(), UUID.randomUUID(), sku, "Frame " + sku, quantity,
                unitPriceCents);
    }
}
