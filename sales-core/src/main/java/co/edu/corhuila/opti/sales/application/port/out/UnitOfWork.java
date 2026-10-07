package co.edu.corhuila.opti.sales.application.port.out;

import java.util.function.Supplier;

/** Runs a block atomically, whatever the store is. The core never sees a transaction API. */
public interface UnitOfWork {

    <T> T run(Supplier<T> work);
}
