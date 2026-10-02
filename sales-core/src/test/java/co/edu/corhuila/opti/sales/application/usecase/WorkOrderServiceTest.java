package co.edu.corhuila.opti.sales.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.corhuila.opti.sales.application.port.in.InvoiceUseCases.InvoiceFilter;
import co.edu.corhuila.opti.sales.application.port.in.PageQuery;
import co.edu.corhuila.opti.sales.application.port.in.WorkOrderUseCases.WorkOrderFilter;
import co.edu.corhuila.opti.sales.domain.model.DomainException;
import co.edu.corhuila.opti.sales.domain.model.ErrorKind;
import co.edu.corhuila.opti.sales.domain.model.FieldError;
import co.edu.corhuila.opti.sales.domain.model.Invoice;
import co.edu.corhuila.opti.sales.domain.model.InvoiceStatus;
import co.edu.corhuila.opti.sales.domain.model.Payment;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;
import co.edu.corhuila.opti.sales.domain.model.WorkOrder;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderItem;
import co.edu.corhuila.opti.sales.domain.model.WorkOrderStatus;
import co.edu.corhuila.opti.sales.testsupport.Fixtures;
import co.edu.corhuila.opti.sales.testsupport.TestClock;

class WorkOrderServiceTest {

    private static final String KEY = "open-order-0001";

    private TestClock clock;
    private Fixtures.Sales sales;

    @BeforeEach
    void setUp() {
        clock = TestClock.at(Fixtures.START);
        sales = Fixtures.sales(clock);
    }

    // ---- opening --------------------------------------------------------------------------

    @Test
    void opensAQuotationWhoseTotalIsTheSumOfItsLinesAndInvoicesIt() {
        var created = sales.orders().open(Fixtures.order("saga-1",
                Fixtures.line("RB5228-2000", 2, 52_000_000L), Fixtures.line("VOG-VO5239", 1, 26_800_000L)), KEY);

        WorkOrder order = created.value();
        assertThat(created.created()).isTrue();
        assertThat(order.status()).isEqualTo(WorkOrderStatus.QUOTATION);
        assertThat(order.number()).isEqualTo("OT-0001");
        assertThat(order.totalCents()).isEqualTo(130_800_000L);
        assertThat(order.items()).hasSize(2);
        Invoice invoice = sales.invoices().search(new InvoiceFilter(null, order.id()), PageQuery.first(5)).data().get(0);
        assertThat(invoice.number()).isEqualTo("FV-0001");
        assertThat(invoice.totalCents()).isEqualTo(130_800_000L);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void repeatingTheKeyReturnsTheSameOrderAndOpensNoSecondInvoice() {
        var first = sales.orders().open(Fixtures.validOrder(), KEY);
        var replay = sales.orders().open(Fixtures.validOrder(), KEY);

        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.value().id());
        assertThat(sales.orders().search(new WorkOrderFilter(null, null, null), PageQuery.first(20)).total()).isEqualTo(1);
        assertThat(sales.invoices().search(new InvoiceFilter(null, null), PageQuery.first(20)).total()).isEqualTo(1);
    }

    @Test
    void reportsEveryInvalidFieldIncludingThoseOfEachLine() {
        var bad = new WorkOrder.Data(null, " ", List.of(
                new WorkOrderItem.Data(null, null, "x", "", 0, -1L),
                Fixtures.line("RB5228-2000", 1, 52_000_000L)));

        assertThatThrownBy(() -> sales.orders().open(bad, "short"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.VALIDATION);
                    assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                            "Idempotency-Key", "patientId", "reference", "items[0].frameId",
                            "items[0].reservationId", "items[0].sku", "items[0].description",
                            "items[0].quantity", "items[0].unitPriceCents");
                });
    }

    @Test
    void anOrderNeedsBetweenOneAndTwentyLines() {
        assertThatThrownBy(() -> sales.orders().open(new WorkOrder.Data(Fixtures.PATIENT, "saga-1", List.of()), KEY))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field).contains("items"));
        var tooMany = new WorkOrder.Data(Fixtures.PATIENT, "saga-1",
                java.util.Collections.nCopies(21, Fixtures.line("RB5228-2000", 1, 1L)));
        assertThatThrownBy(() -> sales.orders().open(tooMany, KEY)).isInstanceOf(DomainException.class);
    }

    @Test
    void aRejectedRequestDoesNotBurnANumberOrLeaveTheKeyTaken() {
        assertThatThrownBy(() -> sales.orders().open(new WorkOrder.Data(null, "saga-1", List.of()), KEY))
                .isInstanceOf(DomainException.class);

        var created = sales.orders().open(Fixtures.validOrder(), KEY);

        assertThat(created.created()).isTrue();
        assertThat(created.value().number()).isEqualTo("OT-0001");
    }

    // ---- transitions ----------------------------------------------------------------------

    @Test
    void approveThenAdvanceWalksTheHappyPath() {
        UUID id = sales.orders().open(Fixtures.validOrder(), KEY).value().id();

        assertThat(sales.orders().approve(id).status()).isEqualTo(WorkOrderStatus.APPROVED);
        assertThat(sales.orders().advance(id).status()).isEqualTo(WorkOrderStatus.IN_LABORATORY);
        assertThat(sales.orders().advance(id).status()).isEqualTo(WorkOrderStatus.READY);
        assertThat(sales.orders().advance(id).status()).isEqualTo(WorkOrderStatus.DELIVERED);
        assertThatThrownBy(() -> sales.orders().advance(id)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.INVALID_STATUS_TRANSITION));
    }

    @Test
    void approvingTwiceOrAdvancingAQuotationIsAnInvalidTransition() {
        UUID id = sales.orders().open(Fixtures.validOrder(), KEY).value().id();

        assertThatThrownBy(() -> sales.orders().advance(id)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.INVALID_STATUS_TRANSITION));
        sales.orders().approve(id);
        assertThatThrownBy(() -> sales.orders().approve(id)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.INVALID_STATUS_TRANSITION));
    }

    @Test
    void cancellingVoidsTheInvoiceAndRepeatingItIsHarmless() {
        UUID id = sales.orders().open(Fixtures.validOrder(), KEY).value().id();

        assertThat(sales.orders().cancel(id).status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(sales.orders().cancel(id).status()).isEqualTo(WorkOrderStatus.CANCELLED);

        Invoice invoice = sales.invoices().search(new InvoiceFilter(null, id), PageQuery.first(5)).data().get(0);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.VOID);
    }

    @Test
    void anOrderInTheLaboratoryCannotBeCancelled() {
        UUID id = sales.orders().open(Fixtures.validOrder(), KEY).value().id();
        sales.orders().approve(id);
        sales.orders().advance(id);

        assertThatThrownBy(() -> sales.orders().cancel(id)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.INVALID_STATUS_TRANSITION));
    }

    @Test
    void anOrderWithPaymentsCannotBeCancelled() {
        WorkOrder order = sales.orders().open(Fixtures.validOrder(), KEY).value();
        Invoice invoice = invoiceOf(order);
        sales.invoices().pay(invoice.id(), payment(1_000_000L), "pay-0000001");

        assertThatThrownBy(() -> sales.orders().cancel(order.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
        assertThat(sales.orders().get(order.id()).status()).isEqualTo(WorkOrderStatus.QUOTATION);
    }

    @Test
    void unknownOrderIsNotFound() {
        assertThatThrownBy(() -> sales.orders().get(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
        assertThatThrownBy(() -> sales.orders().cancel(UUID.randomUUID())).isInstanceOf(DomainException.class);
    }

    @Test
    void filtersByStatusPatientAndCreationDateNewestFirst() {
        var first = sales.orders().open(Fixtures.validOrder(), "open-order-0001").value();
        clock.advance(Duration.ofHours(2));
        var second = sales.orders().open(Fixtures.validOrder(), "open-order-0002").value();
        sales.orders().approve(second.id());

        var all = sales.orders().search(new WorkOrderFilter(null, null, null), PageQuery.first(20));
        var quotations = sales.orders().search(new WorkOrderFilter(WorkOrderStatus.QUOTATION, null, null), PageQuery.first(20));
        var old = sales.orders().search(new WorkOrderFilter(WorkOrderStatus.QUOTATION, null,
                clock.instant().minus(Duration.ofHours(1))), PageQuery.first(20));

        assertThat(all.data()).extracting(WorkOrder::id).containsExactly(second.id(), first.id());
        assertThat(quotations.data()).extracting(WorkOrder::id).containsExactly(first.id());
        assertThat(old.data()).extracting(WorkOrder::id).containsExactly(first.id());
        assertThat(sales.orders().search(new WorkOrderFilter(null, UUID.randomUUID(), null), PageQuery.first(20)).total())
                .isZero();
    }

    // ---- invoices and payments ------------------------------------------------------------

    @Test
    void partialPaymentsLeaveABalanceUntilTheInvoiceIsPaid() {
        Invoice invoice = invoiceOf(sales.orders().open(Fixtures.validOrder(), KEY).value());

        sales.invoices().pay(invoice.id(), payment(80_000_000L), "pay-0000001");
        Invoice partial = sales.invoices().get(invoice.id());
        assertThat(partial.status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(partial.balanceCents()).isEqualTo(24_000_000L);

        sales.invoices().pay(invoice.id(), payment(24_000_000L), "pay-0000002");
        Invoice paid = sales.invoices().get(invoice.id());
        assertThat(paid.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.balanceCents()).isZero();
        assertThat(sales.invoices().payments(invoice.id(), PageQuery.first(10)).total()).isEqualTo(2);
    }

    @Test
    void aPaymentAboveTheBalanceOrOnAPaidOrVoidInvoiceIsRejected() {
        WorkOrder order = sales.orders().open(Fixtures.validOrder(), KEY).value();
        Invoice invoice = invoiceOf(order);

        assertThatThrownBy(() -> sales.invoices().pay(invoice.id(), payment(104_000_001L), "pay-0000001"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.BUSINESS_RULE_VIOLATION));
        sales.invoices().pay(invoice.id(), payment(104_000_000L), "pay-0000002");
        assertThatThrownBy(() -> sales.invoices().pay(invoice.id(), payment(1L), "pay-0000003"))
                .isInstanceOf(DomainException.class);

        WorkOrder other = sales.orders().open(Fixtures.validOrder(), "open-order-0002").value();
        sales.orders().cancel(other.id());
        assertThatThrownBy(() -> sales.invoices().pay(invoiceOf(other).id(), payment(1L), "pay-0000004"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void theSamePaymentKeyRecordsThePaymentOnce() {
        Invoice invoice = invoiceOf(sales.orders().open(Fixtures.validOrder(), KEY).value());

        var first = sales.invoices().pay(invoice.id(), payment(10_000_000L), "pay-0000001");
        var replay = sales.invoices().pay(invoice.id(), payment(10_000_000L), "pay-0000001");

        assertThat(replay.created()).isFalse();
        assertThat(replay.value().id()).isEqualTo(first.value().id());
        assertThat(sales.invoices().get(invoice.id()).paidCents()).isEqualTo(10_000_000L);
    }

    @Test
    void paymentValidationNamesEveryField() {
        Invoice invoice = invoiceOf(sales.orders().open(Fixtures.validOrder(), KEY).value());

        assertThatThrownBy(() -> sales.invoices().pay(invoice.id(), new Payment.Data(0L, null, "r".repeat(101)), "x"))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.fields()).extracting(FieldError::field).containsExactlyInAnyOrder(
                                "Idempotency-Key", "amountCents", "method", "reference"));
    }

    @Test
    void payingAnUnknownInvoiceIsNotFound() {
        assertThatThrownBy(() -> sales.invoices().pay(UUID.randomUUID(), payment(1L), "pay-0000001"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
    }

    private Invoice invoiceOf(WorkOrder order) {
        return sales.invoices().search(new InvoiceFilter(null, order.id()), PageQuery.first(5)).data().get(0);
    }

    private static Payment.Data payment(long cents) {
        return new Payment.Data(cents, PaymentMethod.CASH, "receipt-1");
    }
}
