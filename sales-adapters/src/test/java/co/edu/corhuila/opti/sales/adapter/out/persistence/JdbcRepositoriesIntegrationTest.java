package co.edu.corhuila.opti.sales.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases;
import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases.WorkOrderFilter;
import co.edu.corhuila.opti.sales.adapter.out.gateway.SandboxPaymentGateway;
import co.edu.corhuila.opti.sales.application.usecase.InvoiceService;
import co.edu.corhuila.opti.sales.application.usecase.WorkOrderService;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.InvoiceStatus;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;
import co.edu.corhuila.opti.sales.domain.model.ProductType;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderItem;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;

/**
 * Runs the real use cases over a real PostgreSQL carrying the schema of opti-sales-db.
 * {@code TEST_DATABASE_URL} example: {@code jdbc:postgresql://localhost:5432/sales?user=x&password=y}.
 * Without it the test is skipped, not failed.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRepositoriesIntegrationTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static WorkOrderUseCases orders;
    private static InvoiceUseCases invoices;

    @BeforeAll
    static void connect() {
        var dataSource = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"));
        dataSource.setSchema("sales");
        var jdbc = JdbcClient.create(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var keys = new IdempotencyKeys(jdbc);
        var ids = new UuidGenerator();
        var unit = new JdbcUnitOfWork(transaction);
        var invoiceRepository = new JdbcInvoiceRepository(jdbc);
        orders = new WorkOrderService(new JdbcWorkOrderRepository(jdbc), invoiceRepository,
                new JdbcNumberSequence(jdbc), keys, ids, unit, Clock.systemUTC());
        invoices = new InvoiceService(invoiceRepository, new JdbcPaymentRepository(jdbc), new SandboxPaymentGateway(),
                keys, ids, unit, Clock.systemUTC());
    }

    @Test
    void opensTheOrderItsLinesAndItsInvoiceTogether() {
        var created = orders.open(order(52_000_000L, 2, 26_800_000L, 1), key());

        WorkOrder read = orders.get(created.value().id());

        assertThat(read.number()).matches("OT-\\d{4,}");
        assertThat(read.status()).isEqualTo(WorkOrderStatus.QUOTATION);
        assertThat(read.totalCents()).isEqualTo(130_800_000L);
        assertThat(read.items()).extracting(WorkOrderItem::sku).containsExactly("RB5228-2000", "VOG-VO5239");
        assertThat(read.items().get(0).subtotalCents()).isEqualTo(104_000_000L);
        Invoice invoice = invoiceOf(read);
        assertThat(invoice.number()).matches("FV-\\d{4,}");
        assertThat(invoice.totalCents()).isEqualTo(130_800_000L);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(read.createdAt()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    }

    @Test
    void sameKeyCreatesNothingNewAndAFailedAttemptLeavesTheKeyFree() {
        String key = key();
        var first = orders.open(order(1_000L, 1), key);
        var replay = orders.open(order(1_000L, 1), key);
        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.value().id());

        String lostKey = key();
        var bad = new WorkOrder.Data(PATIENT, "saga", List.of());
        assertThatThrownBy(() -> orders.open(bad, lostKey)).isInstanceOf(DomainException.class);
        assertThat(orders.open(order(1_000L, 1), lostKey).created()).isTrue();
    }

    @Test
    void cancellingVoidsTheInvoiceAndAnOrderWithPaymentsCannotBeCancelled() {
        WorkOrder plain = orders.open(order(5_000L, 1), key()).value();
        assertThat(orders.cancel(plain.id()).status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(orders.cancel(plain.id()).status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(invoiceOf(plain).status()).isEqualTo(InvoiceStatus.VOID);

        WorkOrder paid = orders.open(order(5_000L, 1), key()).value();
        invoices.pay(invoiceOf(paid).id(), new Payment.Data(1_000L, PaymentMethod.CASH, null), key());
        assertThatThrownBy(() -> orders.cancel(paid.id())).isInstanceOf(DomainException.class);
        assertThat(orders.get(paid.id()).status()).isEqualTo(WorkOrderStatus.QUOTATION);
    }

    @Test
    void searchFiltersByStatusPatientAndDate() {
        UUID patient = UUID.randomUUID();
        var data = new WorkOrder.Data(patient, "saga", List.of(line(1_000L, 1)));
        WorkOrder first = orders.open(data, key()).value();
        orders.approve(orders.open(data, key()).value().id());

        var quotations = orders.search(new WorkOrderFilter(WorkOrderStatus.QUOTATION, patient, null, null), PageQuery.first(10));
        var byDate = orders.search(new WorkOrderFilter(null, patient, Instant.now().plusSeconds(60), null), PageQuery.first(10));
        var none = orders.search(new WorkOrderFilter(null, patient, Instant.now().minusSeconds(3600), null), PageQuery.first(10));
        var byNumber = orders.search(new WorkOrderFilter(null, patient, null, first.number()), PageQuery.first(10));

        assertThat(quotations.data()).extracting(WorkOrder::id).containsExactly(first.id());
        assertThat(byDate.total()).isEqualTo(2);
        assertThat(byDate.data().get(0).createdAt()).isAfterOrEqualTo(byDate.data().get(1).createdAt());
        assertThat(none.total()).isZero();
        assertThat(byNumber.data()).extracting(WorkOrder::id).containsExactly(first.id());
    }

    @Test
    void concurrentPaymentsNeverExceedTheBalance() throws Exception {
        Invoice invoice = invoiceOf(orders.open(order(100_000L, 1), key()).value());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String key = key();
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        invoices.pay(invoice.id(), new Payment.Data(40_000L, PaymentMethod.CASH, null), key);
                        return true;
                    } catch (DomainException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long accepted = 0;
            for (Future<Boolean> attempt : attempts) {
                accepted += attempt.get(20, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertThat(accepted).as("only two payments of 40,000 fit in 100,000").isEqualTo(2);
            Invoice after = invoices.get(invoice.id());
            assertThat(after.paidCents()).isEqualTo(80_000L);
            assertThat(after.status()).isEqualTo(InvoiceStatus.PARTIAL);
            assertThat(invoices.payments(invoice.id(), PageQuery.first(10)).total()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void numbersAreUniqueAcrossConcurrentOpenings() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<String>> numbers = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                String key = key();
                numbers.add(pool.submit(() -> orders.open(order(1_000L, 1), key).value().number()));
            }
            var unique = new java.util.HashSet<String>();
            for (Future<String> number : numbers) {
                unique.add(number.get(20, TimeUnit.SECONDS));
            }
            assertThat(unique).hasSize(12);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Invoice invoiceOf(WorkOrder order) {
        return invoices.search(new InvoiceFilter(null, order.id()), PageQuery.first(5)).data().get(0);
    }

    private static WorkOrder.Data order(long price, int quantity) {
        return new WorkOrder.Data(PATIENT, "saga-it", List.of(line(price, quantity)));
    }

    private static WorkOrder.Data order(long price1, int quantity1, long price2, int quantity2) {
        return new WorkOrder.Data(PATIENT, "saga-it", List.of(
                new WorkOrderItem.Data(ProductType.FRAME, UUID.randomUUID(), UUID.randomUUID(), "RB5228-2000",
                        "Frame Ray-Ban", quantity1, price1),
                new WorkOrderItem.Data(ProductType.FRAME, UUID.randomUUID(), UUID.randomUUID(), "VOG-VO5239",
                        "Frame Vogue", quantity2, price2)));
    }

    private static WorkOrderItem.Data line(long price, int quantity) {
        return new WorkOrderItem.Data(ProductType.FRAME, UUID.randomUUID(), UUID.randomUUID(), "RB5228-2000",
                "Frame Ray-Ban", quantity, price);
    }

    private static String key() {
        return "it-" + UUID.randomUUID();
    }
}
