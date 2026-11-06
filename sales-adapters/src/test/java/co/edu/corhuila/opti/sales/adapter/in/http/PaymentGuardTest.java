package co.edu.corhuila.opti.sales.adapter.in.http;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import co.edu.corhuila.opti.sales.testsupport.TestClock;
import co.edu.corhuila.opti.sales.application.port.out.Created;
import co.edu.corhuila.opti.sales.domain.model.*;

class PaymentGuardTest {
    private final TestClock clock = TestClock.at("2026-10-01T12:00:00Z");
    private final UUID invoice = UUID.randomUUID();
    private final Payment.Data data = new Payment.Data(100L, PaymentMethod.PSE, "PRUEBA");
    private final AtomicInteger calls = new AtomicInteger();
    private final PaymentGuard guard = new PaymentGuard(clock, new PaymentGuard.Store() {
        public <T> T transaction(Supplier<T> work) { return work.get(); }
        public void save(UUID payment, UUID invoice, PaymentGuard.Document document) { }
        public Optional<PaymentGuard.Document> get(UUID payment, UUID invoice) { return Optional.empty(); }
        public boolean exists(UUID payment, UUID invoice) { return false; }
    });
    private Created<Payment> action() {
        calls.incrementAndGet();
        return new Created<>(Payment.register(UUID.randomUUID(), invoice, Payment.check(data), "SANDBOX-TEST", clock.instant()), true);
    }
    @Test void expirationAndThreeAttemptsFailClosed() {
        var challenge = guard.start("test-user", invoice, data);
        String wrong = challenge.testCode().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) assertThatThrownBy(() -> guard.pay("test-user", invoice, data, "test-key-1", challenge.id(), wrong, null, this::action)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> guard.pay("test-user", invoice, data, "test-key-1", challenge.id(), challenge.testCode(), null, this::action)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> guard.start("test-user", invoice, data)).isInstanceOf(ApiException.class);
        clock.advance(Duration.ofSeconds(180));
        assertThatThrownBy(() -> guard.pay("test-user", invoice, data, "test-key-1", challenge.id(), challenge.testCode(), null, this::action)).isInstanceOf(ApiException.class);
        assertThat(calls).hasValue(0);
        assertThat(guard.start("test-user", invoice, data).id()).isNotEqualTo(challenge.id());
    }
    @Test void bindsCallerInvoiceAmountAndMethod() {
        var challenge = guard.start("test-user", invoice, data);
        assertThatThrownBy(() -> guard.pay("other", invoice, data, "test-key-1", challenge.id(), challenge.testCode(), null, this::action)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> guard.pay("test-user", UUID.randomUUID(), data, "test-key-1", challenge.id(), challenge.testCode(), null, this::action)).isInstanceOf(ApiException.class);
        for (var changed : List.of(new Payment.Data(101L, PaymentMethod.PSE, "PRUEBA"), new Payment.Data(100L, PaymentMethod.CARD, "PRUEBA"), new Payment.Data(100L, PaymentMethod.PSE, "OTRO")))
            assertThatThrownBy(() -> guard.pay("test-user", invoice, changed, "test-key-1", challenge.id(), challenge.testCode(), null, this::action)).isInstanceOf(ApiException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void concurrentConfirmationAndRetryCreateExactlyOnePayment() throws Exception {
        var challenge = guard.start("test-user", invoice, data);
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Callable<Created<Payment>>> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) jobs.add(() -> guard.pay("test-user", invoice, data, "test-key-1", challenge.id(), challenge.testCode(), null, this::action));
            var results = pool.invokeAll(jobs);
            UUID id = results.get(0).get().value().id();
            for (var result : results) assertThat(result.get().value().id()).isEqualTo(id);
        }
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> guard.pay("test-user", invoice, data, "test-key-2", challenge.id(), challenge.testCode(), null, this::action)).isInstanceOf(ApiException.class);
    }
    @Test void allManualEvidenceMethodsRejectMissingEmptySpoofedOrOversizedFiles() {
        for (var method : List.of(PaymentMethod.TRANSFER, PaymentMethod.NEQUI, PaymentMethod.DAVIPLATA)) {
            var manual = new Payment.Data(100L, method, "PRUEBA");
            assertThatThrownBy(() -> guard.pay("test-user", invoice, manual, "test-key-1", null, null, null, this::action)).isInstanceOf(ApiException.class);
        }
        for (var receipt : List.of(new PaymentGuard.Receipt("text/html", "JVBERi0="), new PaymentGuard.Receipt("image/png", "JVBERi0="), new PaymentGuard.Receipt("application/pdf", ""), new PaymentGuard.Receipt("application/pdf", "%%%"), new PaymentGuard.Receipt("application/pdf", "A".repeat(3_000_000))))
            assertThatThrownBy(() -> PaymentGuard.validate(receipt)).isInstanceOf(ApiException.class);
        assertThat(PaymentGuard.validate(new PaymentGuard.Receipt("application/pdf", "JVBERi0xLjQK")).bytes()).isNotEmpty();
        assertThat(calls).hasValue(0);
    }
}
