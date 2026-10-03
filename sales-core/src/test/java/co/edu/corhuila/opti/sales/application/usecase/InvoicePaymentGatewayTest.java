package co.edu.corhuila.opti.sales.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.domain.model.ErrorKind;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;
import co.edu.corhuila.opti.sales.testsupport.FakePaymentGateway;
import co.edu.corhuila.opti.sales.testsupport.Fixtures;
import co.edu.corhuila.opti.sales.testsupport.TestClock;

/** Electronic payment methods (CARD, PSE, NEQUI, DAVIPLATA) are authorized through the gateway first. */
class InvoicePaymentGatewayTest {

    private static final String KEY = "open-order-0001";

    private TestClock clock;
    private FakePaymentGateway gateway;
    private Fixtures.Sales sales;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        gateway = new FakePaymentGateway();
        sales = Fixtures.sales(clock, gateway);
        var order = sales.orders().open(Fixtures.validOrder(), KEY).value();
        invoice = sales.invoices().search(new InvoiceFilter(null, order.id()), PageQuery.first(5)).data().get(0);
    }

    @Test
    void cashNeverCallsTheGateway() {
        sales.invoices().pay(invoice.id(), new Payment.Data(10_000L, PaymentMethod.CASH, "receipt-1"), "pay-0001");

        assertThat(gateway.authorized).isEmpty();
    }

    @Test
    void anApprovedCardPaymentRecordsTheGatewayTransactionId() {
        var payment = sales.invoices()
                .pay(invoice.id(), new Payment.Data(10_000L, PaymentMethod.CARD, "receipt-1"), "pay-0001")
                .value();

        assertThat(gateway.authorized).containsExactly(PaymentMethod.CARD);
        assertThat(payment.gatewayTransactionId()).startsWith("TEST-");
    }

    @Test
    void aDeclinedPaymentIsNeverRecordedAndTheInvoiceIsUntouched() {
        gateway.approve = false;
        gateway.declineReason = "insufficient funds";

        assertThatThrownBy(() -> sales.invoices()
                .pay(invoice.id(), new Payment.Data(10_000L, PaymentMethod.CARD, "receipt-1"), "pay-0001"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION);
                    assertThat(e.getMessage()).contains("insufficient funds");
                });
        assertThat(sales.invoices().get(invoice.id()).paidCents()).isZero();
        assertThat(sales.invoices().payments(invoice.id(), PageQuery.first(10)).total()).isZero();
    }

    @Test
    void retryingWithTheSameKeyAfterADeclineCanStillSucceed() {
        gateway.approve = false;
        assertThatThrownBy(() -> sales.invoices()
                .pay(invoice.id(), new Payment.Data(10_000L, PaymentMethod.CARD, "receipt-1"), "pay-0001"))
                .isInstanceOf(DomainException.class);

        gateway.approve = true;
        var retried = sales.invoices()
                .pay(invoice.id(), new Payment.Data(10_000L, PaymentMethod.CARD, "receipt-1"), "pay-0001");

        assertThat(retried.created()).isTrue();
        assertThat(sales.invoices().get(invoice.id()).paidCents()).isEqualTo(10_000L);
    }
}
