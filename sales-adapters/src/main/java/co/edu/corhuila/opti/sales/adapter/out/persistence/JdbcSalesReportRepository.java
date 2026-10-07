package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.SalesSummary;
import co.edu.corhuila.opti.sales.application.port.out.SalesReportRepository;

/** Aggregates work_order with one query: CANCELLED orders never count towards revenue. */
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
}
