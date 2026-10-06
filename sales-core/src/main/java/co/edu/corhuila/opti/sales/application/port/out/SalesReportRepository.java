package co.edu.corhuila.opti.sales.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.DailySales;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.SalesSummary;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.StatusCount;

/** Read-only aggregation of work orders, over non-cancelled orders only. */
public interface SalesReportRepository {

    /** {@code sellerId} null aggregates across every seller. Either bound of the period may be null. */
    SalesSummary aggregate(UUID sellerId, Instant from, Instant to);

    /**
     * One row per day that had at least one non-CANCELLED order in {@code [from, to)}; days
     * without sales are simply absent, the caller fills the gaps.
     */
    List<DailySales> dailyTotals(Instant from, Instant to);

    /** One row per status that has at least one work order; statuses with zero orders are absent. */
    List<StatusCount> countsByStatus();
}
