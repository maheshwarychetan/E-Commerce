package com.example.ecommerce.repository;

import com.example.ecommerce.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** Plain, non-locking read - safe for display purposes (menu option 8, product listing, etc). */
    Optional<Inventory> findByProductId(Long productId);

    /**
     * Locks the inventory row for the given product using PESSIMISTIC_WRITE
     * (translates to "SELECT ... FOR UPDATE" on PostgreSQL).
     *
     * WHY THIS LOCK IS REQUIRED:
     * Without it, two concurrent checkout transactions could each read the
     * same "available quantity" before either has committed its update -
     * both would see enough stock and both would proceed, resulting in
     * oversold inventory (the classic lost-update race condition). Example:
     *
     *   Without locking:
     *     Transaction A reads stock = 5
     *     Transaction B reads stock = 5
     *     A buys 4, B buys 4 -> both think it's available
     *     Result: 8 units "sold" when only 5 existed.
     *
     *   With PESSIMISTIC_WRITE:
     *     Transaction A locks the inventory row
     *     Transaction B blocks, waiting for the row lock
     *     A updates stock and commits, releasing the lock
     *     B then acquires the lock and reads the UPDATED stock
     *     B fails with InsufficientInventoryException if that's not enough
     *
     * This serializes access to a single product's inventory row and is
     * what OrderService.createOrder and OrderService.cancelOrder call
     * before reading or mutating quantity/reservedQuantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);
}
