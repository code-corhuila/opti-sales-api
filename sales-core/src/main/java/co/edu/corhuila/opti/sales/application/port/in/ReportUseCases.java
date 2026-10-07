package co.edu.corhuila.opti.sales.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** What the sales service offers about revenue: a global summary and a seller's own figures. */
public interface ReportUseCases {

    /** Revenue and order count across every seller, in the given period. */
    SalesSummary salesSummary(ReportPeriod period);

    /** Revenue and order count of one seller, in the given period. */
    SalesSummary mySales(UUID sellerId, ReportPeriod period);

    /** An optional window; either bound can be absent, meaning unbounded on that side. */
    record ReportPeriod(Instant from, Instant to) {

        public static final ReportPeriod ALL = new ReportPeriod(null, null);
    }

    /** CANCELLED orders are never counted: they were never an actual sale. */
    record SalesSummary(long ordersCount, long totalCents) {
    }
}
