package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.DailySales;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.SalesSummary;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.StatusCount;
import co.edu.corhuila.opti.sales.application.port.out.SalesReportRepository;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/**
 * Aggregates work_order with one query per read. There is no separate invoice/payment table to
 * report from in this service, so, like {@link #aggregate}, every figure here is read straight off
 * work_order: CANCELLED orders never count towards revenue, but they do count in the status
 * breakdown, since that endpoint is about the orders' lifecycle, not about revenue.
 */
public class JdbcSalesReportRepository implements SalesReportRepository {

    private final JdbcClient jdbc;

    public JdbcSalesReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SalesSummary aggregate(UUID sellerId, Instant from, Instant to) {
        StringBuilder where = new StringBuilder(" WHERE status <> 'CANCELLED'");
        Map<String, Object> params = new HashMap<>();
        if (sellerId != null) {
            where.append(" AND seller_id = :seller");
            params.put("seller", sellerId);
        }
        if (from != null) {
            where.append(" AND created_at >= :from");
            params.put("from", Sql.ts(from));
        }
        if (to != null) {
            where.append(" AND created_at < :to");
            params.put("to", Sql.ts(to));
        }
        return jdbc.sql("SELECT count(*) AS orders_count, coalesce(sum(total_cents), 0) AS total_cents"
                        + " FROM work_order" + where)
                .params(params)
                .query((rs, row) -> new SalesSummary(rs.getLong("orders_count"), rs.getLong("total_cents")))
                .single();
    }

    @Override
    public List<DailySales> dailyTotals(Instant from, Instant to) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", Sql.ts(from));
        params.put("to", Sql.ts(to));
        return jdbc.sql("SELECT (created_at AT TIME ZONE 'UTC')::date AS day,"
                        + " coalesce(sum(total_cents), 0) AS total_cents"
                        + " FROM work_order"
                        + " WHERE status <> 'CANCELLED' AND created_at >= :from AND created_at < :to"
                        + " GROUP BY day ORDER BY day")
                .params(params)
                .query((rs, row) -> new DailySales(Sql.date(rs, "day"), rs.getLong("total_cents")))
                .list();
    }

    @Override
    public List<StatusCount> countsByStatus() {
        return jdbc.sql("SELECT status, count(*) AS cnt FROM work_order GROUP BY status")
                .query((rs, row) -> new StatusCount(WorkOrderStatus.valueOf(rs.getString("status")),
                        rs.getLong("cnt")))
                .list();
    }
}
