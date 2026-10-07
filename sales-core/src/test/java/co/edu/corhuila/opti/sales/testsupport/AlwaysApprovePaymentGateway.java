package co.edu.corhuila.opti.sales.testsupport;

import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** Fake of the payment gateway that always approves, for tests that do not care about declines. */
public final class AlwaysApprovePaymentGateway implements PaymentGateway {

    public static final AlwaysApprovePaymentGateway INSTANCE = new AlwaysApprovePaymentGateway();

    @Override
    public GatewayResult authorize(PaymentMethod method, long amountCents, String reference) {
        return new GatewayResult(true, "TEST-" + UUID.randomUUID(), null);
    }
}
