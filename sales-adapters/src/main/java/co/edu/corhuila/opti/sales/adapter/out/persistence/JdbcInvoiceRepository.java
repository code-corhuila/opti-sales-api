package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.out.InvoiceRepository;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.InvoiceStatus;

/** PostgreSQL implementation of {@link InvoiceRepository}. */
public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String COLUMNS = """
            id, number, work_order_id, patient_id, total_cents, paid_cents, status, created_at, updated_at""";

    private final JdbcClient jdbc;

    public JdbcInvoiceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Invoice i) {
        jdbc.sql("INSERT INTO invoice (" + COLUMNS + ") VALUES (:id, :number, :order, :patient, :total, :paid,"
                        + " :status, :createdAt, :updatedAt)")
                .param("id", i.id()).param("number", i.number()).param("order", i.workOrderId())
                .param("patient", i.patientId()).param("total", i.totalCents()).param("paid", i.paidCents())
                .param("status", i.status().name()).param("createdAt", Sql.ts(i.createdAt()))
                .param("updatedAt", Sql.ts(i.updatedAt()))
                .update();
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invoice WHERE id = :id")
                .param("id", id).query(JdbcInvoiceRepository::map).optional();
    }

    @Override
    public Optional<Invoice> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invoice WHERE id = :id FOR UPDATE")
                .param("id", id).query(JdbcInvoiceRepository::map).optional();
    }

    @Override
    public Optional<Invoice> findByWorkOrderIdForUpdate(UUID workOrderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invoice WHERE work_order_id = :order FOR UPDATE")
                .param("order", workOrderId).query(JdbcInvoiceRepository::map).optional();
    }

    @Override
    public PageResult<Invoice> search(InvoiceFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (filter.status() != null) {
            conditions.add("status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.workOrderId() != null) {
            conditions.add("work_order_id = :order");
            params.put("order", filter.workOrderId());
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbc.sql("SELECT count(*) FROM invoice" + where).params(params).query(Long.class).single();
        List<Invoice> rows = jdbc.sql("SELECT " + COLUMNS + " FROM invoice" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcInvoiceRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    @Override
    public void update(Invoice i) {
        jdbc.sql("UPDATE invoice SET paid_cents = :paid, status = :status, updated_at = :updatedAt WHERE id = :id")
                .param("paid", i.paidCents()).param("status", i.status().name())
                .param("updatedAt", Sql.ts(i.updatedAt())).param("id", i.id())
                .update();
    }

    private static Invoice map(ResultSet rs, int row) throws SQLException {
        return Invoice.rehydrate(rs.getObject("id", UUID.class), rs.getString("number"),
                rs.getObject("work_order_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getLong("total_cents"), rs.getLong("paid_cents"), InvoiceStatus.valueOf(rs.getString("status")),
                Sql.instant(rs, "created_at"), Sql.instant(rs, "updated_at"));
    }
}
