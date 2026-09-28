package com.example.ecommerce;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.OrderItemRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.InsufficientInventoryException;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates that PESSIMISTIC_WRITE row locking on the inventory row
 * prevents two concurrent checkouts from overselling the same limited
 * stock. Requires a real PostgreSQL instance - row-level locking semantics
 * are exactly what is under test here, and an in-memory database would not
 * exercise the same locking behavior.
 */
@SpringBootTest
class ConcurrencyCheckoutTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderService orderService;

    private Product laptop;
    private Customer customerA;
    private Customer customerB;

    @BeforeEach
    void setUp() {
        laptop = productRepository.save(new Product("Concurrency Laptop", "d", new BigDecimal("75000"), "CC-LAP-" + System.nanoTime()));
        inventoryRepository.save(new Inventory(laptop, 5));
        customerA = customerRepository.save(new Customer("Customer A", "a" + System.nanoTime() + "@example.com", "1"));
        customerB = customerRepository.save(new Customer("Customer B", "b" + System.nanoTime() + "@example.com", "2"));
    }

    @AfterEach
    void tearDown() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void onlyOneOfTwoOversellingConcurrentOrdersSucceeds() throws InterruptedException {
        // Initial stock = 5. Order A wants 4, Order B wants 3 - together
        // they exceed available stock, so exactly one must fail.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Callable<Void> attemptA = buildAttempt(customerA.getId(), 4, startLatch, successCount, failureCount);
        Callable<Void> attemptB = buildAttempt(customerB.getId(), 3, startLatch, successCount, failureCount);

        Future<Void> futureA = executor.submit(attemptA);
        Future<Void> futureB = executor.submit(attemptB);

        startLatch.countDown();

        waitQuietly(futureA);
        waitQuietly(futureB);
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Exactly one of the two overselling attempts should succeed");
        assertEquals(1, failureCount.get(), "Exactly one of the two overselling attempts should fail with InsufficientInventoryException");

        int finalQuantity = inventoryRepository.findByProductId(laptop.getId()).orElseThrow().getQuantity();
        // Whichever attempt wins the race, the final quantity must reflect
        // EXACTLY ONE successful deduction: either 5-4=1 (A won) or 5-3=2
        // (B won). It must never be negative and never both succeed.
        assertTrue(finalQuantity == 1 || finalQuantity == 2,
                "Final quantity must reflect exactly one successful deduction, got " + finalQuantity);
    }

    private Callable<Void> buildAttempt(Long customerId, int quantity, CountDownLatch startLatch,
                                         AtomicInteger successCount, AtomicInteger failureCount) {
        return () -> {
            startLatch.await();
            try {
                orderService.createOrder(new CreateOrderRequest(customerId, List.of(new OrderItemRequest(laptop.getId(), quantity))));
                successCount.incrementAndGet();
            } catch (InsufficientInventoryException e) {
                failureCount.incrementAndGet();
            }
            return null;
        };
    }

    private void waitQuietly(Future<Void> future) {
        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Outcome is already recorded via the success/failure counters above.
        }
    }
}
