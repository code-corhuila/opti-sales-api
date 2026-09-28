package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.PageResult;
import co.edu.corhuila.opti.sales.application.port.out.PaymentRepository;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** PostgreSQL implementation of {@link PaymentRepository}. */
public class JdbcPaymentRepository implements PaymentRepository {

    private static final String COLUMNS = "id, invoice_id, amount_cents, method, reference, paid_at";

    private final JdbcClient jdbc;

    public JdbcPaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Payment p) {
        jdbc.sql("INSERT INTO payment (" + COLUMNS + ") VALUES (:id, :invoice, :amount, :method, :reference, :paidAt)")
                .param("id", p.id()).param("invoice", p.invoiceId()).param("amount", p.amountCents())
                .param("method", p.method().name()).param("reference", p.reference())
                .param("paidAt", Sql.ts(p.paidAt()))
                .update();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payment WHERE id = :id")
                .param("id", id).query(JdbcPaymentRepository::map).optional();
    }

    @Override
    public PageResult<Payment> list(UUID invoiceId, PageQuery page) {
        long total = jdbc.sql("SELECT count(*) FROM payment WHERE invoice_id = :invoice")
                .param("invoice", invoiceId).query(Long.class).single();
        List<Payment> rows = jdbc.sql("SELECT " + COLUMNS + " FROM payment WHERE invoice_id = :invoice"
                        + " ORDER BY paid_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("invoice", invoiceId).param("limit", page.limit()).param("offset", page.offset())
                .query(JdbcPaymentRepository::map).list();
        return new PageResult<>(rows, page.page(), page.limit(), total);
    }

    private static Payment map(ResultSet rs, int row) throws SQLException {
        return new Payment(rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class),
                rs.getLong("amount_cents"), PaymentMethod.valueOf(rs.getString("method")),
                rs.getString("reference"), Sql.instant(rs, "paid_at"));
    }
}
