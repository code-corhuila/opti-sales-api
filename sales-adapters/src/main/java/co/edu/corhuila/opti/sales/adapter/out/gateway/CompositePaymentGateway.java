package co.edu.corhuila.opti.sales.adapter.out.gateway;

import co.edu.corhuila.opti.sales.application.port.out.PaymentGateway;
import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** Routes NEQUI to the real Wompi gateway; everything else (CARD, PSE, DAVIPLATA) to the sandbox. */
public class CompositePaymentGateway implements PaymentGateway {

    private final PaymentGateway nequi;
    private final PaymentGateway fallback;

    public CompositePaymentGateway(PaymentGateway nequi, PaymentGateway fallback) {
        this.nequi = nequi;
        this.fallback = fallback;
    }

    @Override
    public GatewayResult authorize(PaymentMethod method, long amountCents, String reference) {
        if (method == PaymentMethod.NEQUI) {
            return nequi.authorize(method, amountCents, reference);
        }
        return fallback.authorize(method, amountCents, reference);
    }
}
