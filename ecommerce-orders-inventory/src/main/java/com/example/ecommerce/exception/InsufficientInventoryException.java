package com.example.ecommerce.exception;

public class InsufficientInventoryException extends RuntimeException {

    private final String productName;
    private final int requested;
    private final int available;

    public InsufficientInventoryException(String productName, int requested, int available) {
        super(String.format(
                "Insufficient inventory for product '%s'. Requested: %d, Available: %d",
                productName, requested, available));
        this.productName = productName;
        this.requested = requested;
        this.available = available;
    }

    public String getProductName() {
        return productName;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
