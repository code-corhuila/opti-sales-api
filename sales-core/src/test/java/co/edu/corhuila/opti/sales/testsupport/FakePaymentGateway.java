package co.edu.corhuila.opti.sales.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** Fake of the payment gateway whose outcome a test controls, and which records every call it saw. */
public final class FakePaymentGateway implements PaymentGateway {

    public boolean approve = true;
    public String declineReason = "insufficient funds";
    public final List<PaymentMethod> authorized = new ArrayList<>();

    @Override
    public GatewayResult authorize(PaymentMethod method, long amountCents, String reference) {
        authorized.add(method);
        if (!approve) {
            return new GatewayResult(false, null, declineReason);
        }
        return new GatewayResult(true, "TEST-" + UUID.randomUUID(), null);
    }
}
