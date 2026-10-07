package co.edu.corhuila.opti.sales.application.port.out;

import co.edu.corhuila.opti.sales.domain.model.PaymentMethod;

/** The external payment gateway that authorizes CARD, PSE, NEQUI and DAVIPLATA payments. */
public interface PaymentGateway {

    /** {@code reference} is the invoice reference shown to the customer; never a card or account number. */
    GatewayResult authorize(PaymentMethod method, long amountCents, String reference);

    /** {@code declineReason} is a short, customer-safe reason; present only when {@code approved} is false. */
    record GatewayResult(boolean approved, String transactionId, String declineReason) {
    }
}
