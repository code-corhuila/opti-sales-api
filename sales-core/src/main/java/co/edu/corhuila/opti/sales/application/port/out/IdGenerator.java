package co.edu.corhuila.opti.sales.application.port.out;

import java.util.UUID;

/** Source of identifiers, so use cases stay deterministic under test. */
public interface IdGenerator {

    UUID next();
}
