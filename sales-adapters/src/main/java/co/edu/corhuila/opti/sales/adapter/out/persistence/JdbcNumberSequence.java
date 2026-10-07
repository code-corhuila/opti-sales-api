package co.edu.corhuila.opti.sales.adapter.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.out.NumberSequence;

/** Numbers taken from PostgreSQL sequences: unique even with concurrent requests, gaps are possible. */
public class JdbcNumberSequence implements NumberSequence {

    private final JdbcClient jdbc;

    public JdbcNumberSequence(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String nextWorkOrderNumber() {
        return "OT-%04d".formatted(next("work_order_number_seq"));
    }

    @Override
    public String nextInvoiceNumber() {
        return "FV-%04d".formatted(next("invoice_number_seq"));
    }

    private long next(String sequence) {
        return jdbc.sql("SELECT nextval('" + sequence + "')").query(Long.class).single();
    }
}
