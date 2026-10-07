package co.edu.corhuila.opti.sales.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases;
import co.edu.corhuila.opti.sales.application.port.in.ReportUseCases.ReportPeriod;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.testsupport.Fixtures;
import co.edu.corhuila.opti.sales.testsupport.TestClock;

class ReportServiceTest {

    private static final UUID SELLER_A = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SELLER_B = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private TestClock clock;
    private WorkOrderUseCases orders;
    private ReportUseCases reports;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        var sales = Fixtures.sales(clock);
        orders = sales.orders();
        reports = sales.reports();
    }

    @Test
    void summarizesEveryOrderAcrossSellers() {
        openFor(SELLER_A, "ref-1");
        openFor(SELLER_B, "ref-2");

        var summary = reports.salesSummary(ReportUseCases.ReportPeriod.ALL);

        assertThat(summary.ordersCount()).isEqualTo(2);
        assertThat(summary.totalCents()).isEqualTo(208_000_000L);
    }

    @Test
    void mySalesOnlyCountsThatSeller() {
        openFor(SELLER_A, "ref-1");
        openFor(SELLER_A, "ref-2");
        openFor(SELLER_B, "ref-3");

        var mine = reports.mySales(SELLER_A, ReportUseCases.ReportPeriod.ALL);

        assertThat(mine.ordersCount()).isEqualTo(2);
        assertThat(mine.totalCents()).isEqualTo(208_000_000L);
    }

    @Test
    void cancelledOrdersAreExcluded() {
        var created = openFor(SELLER_A, "ref-1");
        orders.cancel(created);

        var mine = reports.mySales(SELLER_A, ReportUseCases.ReportPeriod.ALL);

        assertThat(mine.ordersCount()).isZero();
        assertThat(mine.totalCents()).isZero();
    }

    @Test
    void filtersByPeriod() {
        openFor(SELLER_A, "ref-1");
        clock.advance(Duration.ofDays(2));
        openFor(SELLER_A, "ref-2");

        Instant cutoff = Instant.parse(Fixtures.START).plus(Duration.ofDays(1));
        var recent = reports.mySales(SELLER_A, new ReportPeriod(cutoff, null));

        assertThat(recent.ordersCount()).isEqualTo(1);
    }

    @Test
    void mySalesRequiresASeller() {
        assertThatThrownBy(() -> reports.mySales(null, ReportUseCases.ReportPeriod.ALL))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsAFromAfterTo() {
        Instant now = Instant.parse(Fixtures.START);
        assertThatThrownBy(() -> reports.salesSummary(new ReportPeriod(now, now.minusSeconds(60))))
                .isInstanceOf(DomainException.class);
    }

    private UUID openFor(UUID sellerId, String reference) {
        var data = Fixtures.order(reference, Fixtures.line("RB5228-2000", 2, 52_000_000L));
        var withSeller = new co.edu.corhuila.opti.sales.domain.model.WorkOrder.Data(
                data.patientId(), data.reference(), data.items(), sellerId);
        return orders.open(withSeller, "key-" + reference).value().id();
    }
}
