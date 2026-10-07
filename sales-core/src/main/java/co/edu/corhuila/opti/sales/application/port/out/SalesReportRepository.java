package co.edu.corhuila.opti.sales.application.port.out;

import java.time.Instant;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.SalesSummary;

/** Read-only aggregation of work orders, over non-cancelled orders only. */
public interface SalesReportRepository {

    /** {@code sellerId} null aggregates across every seller. Either bound of the period may be null. */
    SalesSummary aggregate(UUID sellerId, Instant from, Instant to);
}
