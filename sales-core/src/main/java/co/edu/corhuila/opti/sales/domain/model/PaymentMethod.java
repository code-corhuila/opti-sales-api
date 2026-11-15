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

    /** CARD and PSE use the local test gateway; wallets and transfers are manually evidenced. */
    public boolean isElectronic() {
        return this == CARD || this == PSE;
    }
}
