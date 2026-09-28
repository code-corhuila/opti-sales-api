package co.edu.corhuila.opti.sales.application.port.out;

/** Human-readable, gap-tolerant numbers of this domain (OT-0001, FV-0001). */
public interface NumberSequence {

    String nextWorkOrderNumber();

    String nextInvoiceNumber();
}
