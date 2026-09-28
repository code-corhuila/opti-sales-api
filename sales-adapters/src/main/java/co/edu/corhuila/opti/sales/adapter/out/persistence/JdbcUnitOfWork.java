package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionTemplate;

import co.edu.corhuila.opti.sales.application.port.out.UnitOfWork;

/** {@link UnitOfWork} backed by a database transaction. Joins an outer one when present. */
public class JdbcUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transaction;

    public JdbcUnitOfWork(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    @Override
    public <T> T run(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }
}
