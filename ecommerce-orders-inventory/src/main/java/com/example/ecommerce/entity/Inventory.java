package com.example.ecommerce.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_product_id", columnNames = "product_id")
})
@Check(constraints = "quantity >= 0 AND reserved_quantity >= 0")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /*
     * Reserved for a future two-phase (reserve -> confirm/release) checkout
     * flow, e.g. one where payment is processed asynchronously and stock
     * needs to be held before the order is finally confirmed. In THIS
     * project the checkout is a single synchronous @Transactional method,
     * so we deduct directly from `quantity` and reservedQuantity stays 0 -
     * see OrderService.createOrder for the full explanation.
     */
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Inventory() {
        // required by JPA
    }

    public Inventory(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * The quantity actually free to sell: total on hand minus whatever is
     * reserved. Must never be computed any other way so this rule stays in
     * exactly one place.
     */
    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
