package co.edu.corhuila.opti.sales.adapter.out.gateway;

import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/**
 * Stands in for a real payment gateway (Wompi, PayU, ePayco...) while none is wired in: same
 * contract, no real charge. Mirrors how a real gateway's sandbox mode uses magic test values to
 * let an integration exercise both outcomes without a live transaction: a reference ending in
 * "0000" is declined, exactly as a provider's documented test card numbers would be.
 */
public class SandboxPaymentGateway implements PaymentGateway {

    private static final String DECLINE_SUFFIX = "0000";

    @Override
    public GatewayResult authorize(PaymentMethod method, long amountCents, String reference) {
        if (reference != null && reference.endsWith(DECLINE_SUFFIX)) {
            return new GatewayResult(false, null, "insufficient funds");
        }
        return new GatewayResult(true, "SANDBOX-" + UUID.randomUUID(), null);
    }
}
