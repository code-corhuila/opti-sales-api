package co.edu.corhuila.opti.sales.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.sales.adapter.in.http.PublicPaths;
import co.edu.corhuila.opti.sales.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.opti.sales.adapter.out.persistence.IdempotencyKeys;
import co.edu.corhuila.opti.sales.adapter.out.persistence.JdbcInvoiceRepository;
import co.edu.corhuila.opti.sales.adapter.out.persistence.JdbcNumberSequence;
import co.edu.corhuila.opti.sales.adapter.out.gateway.SandboxPaymentGateway;
import co.edu.corhuila.opti.sales.adapter.out.persistence.JdbcPaymentRepository;
import co.edu.corhuila.opti.sales.adapter.out.persistence.JdbcSalesReportRepository;
import co.edu.corhuila.opti.sales.adapter.out.persistence.JdbcUnitOfWork;
import co.edu.corhuila.opti.sales.adapter.out.persistence.JdbcWorkOrderRepository;
import co.edu.corhuila.opti.sales.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.application.port.out.IdGenerator;
import co.edu.corhuila.opti.sales.application.port.out.IdempotencyStore;
import co.edu.corhuila.opti.sales.application.port.out.InvoiceRepository;
import co.edu.corhuila.opti.sales.application.port.out.NumberSequence;
import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.application.port.out.PaymentRepository;
import co.edu.corhuila.opti.sales.application.port.out.SalesReportRepository;
import co.edu.corhuila.opti.sales.application.port.out.UnitOfWork;
import co.edu.corhuila.opti.sales.application.port.out.WorkOrderRepository;
import co.edu.corhuila.opti.sales.application.usecase.InvoiceService;
import co.edu.corhuila.opti.sales.application.usecase.ReportService;
import co.edu.corhuila.opti.sales.application.usecase.WorkOrderService;

/**
 * Composition root: the only place that knows every concrete type. The numeric limits (server
 * timeouts, pool size, statement timeout, graceful shutdown) are declared with their value in
 * {@code application.yml}, next to this class.
 */
@Configuration
class SalesConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Rs256Verifier tokenVerifier(ObjectMapper json, Clock clock,
                                @Value("${jwt.public-key:}") String publicKey,
                                @Value("${jwt.public-key-file:}") String publicKeyFile) throws IOException {
        String pem = publicKey.isBlank() && !publicKeyFile.isBlank()
                ? Files.readString(Path.of(publicKeyFile)) : publicKey;
        if (pem.isBlank()) {
            throw new IllegalStateException("Set JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE (identity service public key)");
        }
        return new Rs256Verifier(pem, json, clock);
    }

    @Bean
    PublicPaths publicPaths() {
        return PublicPaths.with();
    }

    @Bean
    IdempotencyStore idempotencyStore(JdbcClient jdbc) {
        return new IdempotencyKeys(jdbc);
    }

    @Bean
    UnitOfWork unitOfWork(TransactionTemplate transaction) {
        return new JdbcUnitOfWork(transaction);
    }

    @Bean
    IdGenerator idGenerator() {
        return new UuidGenerator();
    }

    @Bean
    NumberSequence numberSequence(JdbcClient jdbc) {
        return new JdbcNumberSequence(jdbc);
    }

    @Bean
    WorkOrderRepository workOrderRepository(JdbcClient jdbc) {
        return new JdbcWorkOrderRepository(jdbc);
    }

    @Bean
    InvoiceRepository invoiceRepository(JdbcClient jdbc) {
        return new JdbcInvoiceRepository(jdbc);
    }

    @Bean
    PaymentRepository paymentRepository(JdbcClient jdbc) {
        return new JdbcPaymentRepository(jdbc);
    }

    @Bean
    PaymentGateway paymentGateway() {
        return new SandboxPaymentGateway();
    }

    @Bean
    WorkOrderUseCases workOrderUseCases(WorkOrderRepository orders, InvoiceRepository invoices,
                                        NumberSequence numbers, IdempotencyStore keys, IdGenerator ids,
                                        UnitOfWork unitOfWork, Clock clock) {
        return new WorkOrderService(orders, invoices, numbers, keys, ids, unitOfWork, clock);
    }

    @Bean
    InvoiceUseCases invoiceUseCases(InvoiceRepository invoices, PaymentRepository payments, PaymentGateway gateway,
                                    IdempotencyStore keys, IdGenerator ids, UnitOfWork unitOfWork, Clock clock) {
        return new InvoiceService(invoices, payments, gateway, keys, ids, unitOfWork, clock);
    }

    @Bean
    SalesReportRepository salesReportRepository(JdbcClient jdbc) {
        return new JdbcSalesReportRepository(jdbc);
    }

    @Bean
    ReportUseCases reportUseCases(SalesReportRepository reports) {
        return new ReportService(reports);
    }
}
