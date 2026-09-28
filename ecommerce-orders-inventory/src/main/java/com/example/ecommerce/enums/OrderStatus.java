package com.example.ecommerce.enums;

/**
 * Lifecycle states for an Order.
 *
 * PENDING   - transient state used only while an order is being assembled
 *             in memory; orders are persisted directly as CONFIRMED once
 *             checkout succeeds (see OrderService.createOrder).
 * CONFIRMED - checkout succeeded: inventory was deducted and the order is final.
 * CANCELLED - the order was cancelled and its inventory was restored.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
