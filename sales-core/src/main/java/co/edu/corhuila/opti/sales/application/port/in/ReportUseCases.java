package co.edu.corhuila.opti.sales.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/** What the sales service offers about revenue: a global summary and a seller's own figures. */
public interface ReportUseCases {

    /** Revenue and order count across every seller, in the given period. */
    SalesSummary salesSummary(ReportPeriod period);

    /** Revenue and order count of one seller, in the given period. */
    SalesSummary mySales(UUID sellerId, ReportPeriod period);

    /**
     * One entry per day of the current month, from day 1 through today, zero-filled for days
     * without sales. CANCELLED orders are excluded, same as every other revenue figure here.
     */
    List<DailySales> salesTimeseries();

    /** One entry per {@link WorkOrderStatus} value, zero-filled for statuses with no orders yet. */
    List<StatusCount> ordersByStatus();

    /** An optional window; either bound can be absent, meaning unbounded on that side. */
    record ReportPeriod(Instant from, Instant to) {

        public static final ReportPeriod ALL = new ReportPeriod(null, null);
    }

    /** CANCELLED orders are never counted: they were never an actual sale. */
    record SalesSummary(long ordersCount, long totalCents) {
    }

    /** Revenue for a single calendar day, in the service's own UTC calendar. */
    record DailySales(LocalDate date, long totalCents) {
    }

    /** How many work orders currently sit in a given status. */
    record StatusCount(WorkOrderStatus status, long count) {
    }
}
