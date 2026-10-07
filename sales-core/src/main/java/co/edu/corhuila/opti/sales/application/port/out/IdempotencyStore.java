package co.edu.corhuila.opti.sales.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency keys of the domain. A use case claims the key first, inside its unit of work: a
 * concurrent request with the same key waits for the first one to finish and then finds it taken,
 * so the side effects (stock, money, records) happen once.
 */
public interface IdempotencyStore {

    /** True when the key was free and is now bound to {@code resourceId}. */
    boolean claim(String key, String resourceType, UUID resourceId);

    /** The resource bound to the key, when the key was already used. */
    Optional<UUID> find(String key, String resourceType);
}
