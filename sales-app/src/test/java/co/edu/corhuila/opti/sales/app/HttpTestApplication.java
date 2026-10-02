package co.edu.corhuila.opti.sales.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.sales.adapter.in.http.PublicPaths;
import co.edu.corhuila.opti.sales.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.testsupport.Fixtures;
import co.edu.corhuila.opti.sales.testsupport.TestClock;

/**
 * Boots only the HTTP adapter over the in-memory fakes: no database, same filters, same
 * error handling, same controllers as production.
 */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.opti.sales.adapter.in.http",
        exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
class HttpTestApplication {

    @Bean
    TestClock clock() {
        return TestClock.at(Fixtures.START);
    }

    @Bean
    Rs256Verifier verifier(ObjectMapper json, TestClock clock) {
        return new Rs256Verifier(TestTokens.publicKeyPem(), json, clock);
    }

    @Bean
    PublicPaths publicPaths() {
        return PublicPaths.with();
    }

    @Bean
    Fixtures.Sales sales(TestClock clock) {
        return Fixtures.sales(clock);
    }

    @Bean
    WorkOrderUseCases workOrderUseCases(Fixtures.Sales sales) {
        return sales.orders();
    }

    @Bean
    InvoiceUseCases invoiceUseCases(Fixtures.Sales sales) {
        return sales.invoices();
    }
}
