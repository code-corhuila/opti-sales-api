package co.edu.corhuila.opti.sales.application.port.out;

import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** The external payment gateway that authorizes CARD, PSE, NEQUI and DAVIPLATA payments. */
public interface PaymentGateway {

    /**
     * {@code reference} is the free-text reference the seller typed on the payment form; never a
     * card or bank account number. For NEQUI specifically, by convention it carries the
     * customer's Nequi-registered phone number (the only data a Nequi charge needs) - see
     * WompiPaymentGateway.
     */
    GatewayResult authorize(PaymentMethod method, long amountCents, String reference);

    /** {@code declineReason} is a short, customer-safe reason; present only when {@code approved} is false. */
    record GatewayResult(boolean approved, String transactionId, String declineReason) {
    }
}
