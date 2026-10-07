package co.edu.corhuila.opti.sales.testsupport;

import java.util.function.Supplier;

import co.edu.corhuila.opti.sales.application.port.out.UnitOfWork;

/**
 * Runs the block right away. When given the idempotency fake it also undoes the keys claimed by a
 * block that failed, like the database rolls back the key together with the rest of the transaction.
 */
public class DirectUnitOfWork implements UnitOfWork {

    private final InMemoryIdempotencyStore keys;

    public DirectUnitOfWork() {
        this(null);
    }

    public DirectUnitOfWork(InMemoryIdempotencyStore keys) {
        this.keys = keys;
    }

    @Override
    public <T> T run(Supplier<T> work) {
        var snapshot = keys == null ? null : keys.snapshot();
        try {
            return work.get();
        } catch (RuntimeException e) {
            if (keys != null) {
                keys.restore(snapshot);
            }
            throw e;
        }
    }
}
