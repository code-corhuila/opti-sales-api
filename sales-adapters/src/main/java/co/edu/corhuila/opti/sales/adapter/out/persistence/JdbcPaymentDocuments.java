package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import co.edu.corhuila.opti.sales.adapter.in.http.PaymentGuard;

/** Receipt and payment commit together, in the sales database, with no public file route. */
public final class JdbcPaymentDocuments implements PaymentGuard.Store {
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    public JdbcPaymentDocuments(JdbcClient jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }
    @Override public <T> T transaction(Supplier<T> work) { return tx.execute(status -> work.get()); }
    @Override public void save(UUID paymentId, UUID invoiceId, PaymentGuard.Document document) {
        jdbc.sql("INSERT INTO payment_receipt(payment_id, invoice_id, content_type, content) VALUES (:payment, :invoice, :type, :content)")
            .param("payment", paymentId).param("invoice", invoiceId).param("type", document.contentType())
            .param("content", document.bytes()).update();
    }
    @Override public boolean exists(UUID paymentId, UUID invoiceId) {
        return jdbc.sql("SELECT count(*) FROM payment_receipt WHERE payment_id = :payment AND invoice_id = :invoice")
            .param("payment", paymentId).param("invoice", invoiceId).query(Long.class).single() > 0;
    }
    @Override public Optional<PaymentGuard.Document> get(UUID paymentId, UUID invoiceId) {
        return jdbc.sql("SELECT content_type, content FROM payment_receipt WHERE payment_id = :payment AND invoice_id = :invoice")
            .param("payment", paymentId).param("invoice", invoiceId)
            .query((rs, row) -> new PaymentGuard.Document(rs.getString("content_type"), rs.getBytes("content"))).optional();
    }
}
