package co.edu.corhuila.opti.sales.application.port.out;

/**
 * Result of an idempotent creation: {@code created} is false when the idempotency key had
 * already been used and {@code value} is the resource that was created the first time.
 */
public record Created<T>(T value, boolean created) {
}
