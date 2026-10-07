package co.edu.corhuila.opti.sales.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import co.edu.corhuila.opti.sales.application.port.out.IdempotencyStore;

/**
 * Idempotency keys of this domain's database. A key is claimed in the same transaction that
 * writes the resource, so either both exist or neither does.
 */
public class IdempotencyKeys implements IdempotencyStore {

    private final JdbcClient jdbc;

    public IdempotencyKeys(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String key, String resourceType, UUID resourceId) {
        return jdbc.sql("""
                INSERT INTO idempotency_key (key, resource_type, resource_id)
                VALUES (:key, :type, :id) ON CONFLICT (key) DO NOTHING
                """)
                .param("key", key).param("type", resourceType).param("id", resourceId)
                .update() == 1;
    }

    @Override
    public Optional<UUID> find(String key, String resourceType) {
        return jdbc.sql("SELECT resource_id FROM idempotency_key WHERE key = :key AND resource_type = :type")
                .param("key", key).param("type", resourceType)
                .query(UUID.class).optional();
    }
}
