package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.IdGenerator;

/** Random (v4) identifiers. */
public class UuidGenerator implements IdGenerator {

    @Override
    public UUID next() {
        return UUID.randomUUID();
    }
}
