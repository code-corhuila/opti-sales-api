package co.edu.corhuila.opti.sales.domain.model;

/**
 * Which products-domain catalog a work order line's {@code productId} belongs to (HU-25: a sale
 * is no longer only a frame).
 */
public enum ProductType {
    FRAME,
    LENS,
    ACCESSORY,
    LIQUID
}
