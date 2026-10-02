package co.edu.corhuila.opti.sales.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import co.edu.corhuila.opti.sales.application.port.out.IdempotencyStore;

/** Fake of the idempotency table. */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, UUID> byKey = new HashMap<>();

    @Override
    public boolean claim(String key, String resourceType, UUID resourceId) {
        return byKey.putIfAbsent(key, resourceId) == null;
    }

    @Override
    public Optional<UUID> find(String key, String resourceType) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** Copy of the current keys, taken before a unit of work starts. */
    Map<String, UUID> snapshot() {
        return new HashMap<>(byKey);
    }

    /** Puts the keys back as they were: what a rolled-back transaction leaves. */
    void restore(Map<String, UUID> snapshot) {
        byKey.clear();
        byKey.putAll(snapshot);
    }
}
