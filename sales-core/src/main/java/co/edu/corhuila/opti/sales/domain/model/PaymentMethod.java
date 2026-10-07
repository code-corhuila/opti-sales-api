package co.edu.corhuila.opti.sales.domain.model;

/** Ways a patient pays an invoice. */
public enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    PSE,
    NEQUI,
    DAVIPLATA,
    OTHER;

    /** CARD, PSE, NEQUI and DAVIPLATA are authorized through the payment gateway; the rest are a manual entry. */
    public boolean isElectronic() {
        return this == CARD || this == PSE || this == NEQUI || this == DAVIPLATA;
    }
}
