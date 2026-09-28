package com.example.ecommerce;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.OrderItemRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.exception.InsufficientInventoryException;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests that run against a REAL PostgreSQL database - this
 * project deliberately never substitutes H2, because the behavior under
 * test (transactional rollback, row locking) depends on real database
 * semantics. See src/test/resources/application.properties: these tests use
 * a separate "ecommerce_db_test" database so they never touch dev data.
 */
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderService orderService;

    private Product laptop;
    private Product keyboard;
    private Product mouse;
    private Customer customer;

    @BeforeEach
    void setUp() {
        laptop = productRepository.save(new Product("Test Laptop", "d", new BigDecimal("75000"), "IT-LAP-" + System.nanoTime()));
        keyboard = productRepository.save(new Product("Test Keyboard", "d", new BigDecimal("4500"), "IT-KEY-" + System.nanoTime()));
        mouse = productRepository.save(new Product("Test Mouse", "d", new BigDecimal("1500"), "IT-MOU-" + System.nanoTime()));

        inventoryRepository.save(new Inventory(laptop, 10));
        inventoryRepository.save(new Inventory(keyboard, 5));
        inventoryRepository.save(new Inventory(mouse, 0));

        customer = customerRepository.save(new Customer("Test Customer", "test" + System.nanoTime() + "@example.com", "0000000000"));
    }

    @AfterEach
    void tearDown() {
        // Delete children before parents to satisfy foreign keys. Safe only
        // because this points at the disposable ecommerce_db_test database.
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void multiItemOrderRollsBackCompletelyWhenOneItemIsShort() {
        // Laptop = 10, Keyboard = 5, Mouse = 0 - exactly the scenario from
        // the project spec. Attempting to buy 2 laptops, 2 keyboards and 1
        // mouse must fail entirely and leave every inventory row untouched.
        CreateOrderRequest request = new CreateOrderRequest(customer.getId(), List.of(
                new OrderItemRequest(laptop.getId(), 2),
                new OrderItemRequest(keyboard.getId(), 2),
                new OrderItemRequest(mouse.getId(), 1)));

        assertThrows(InsufficientInventoryException.class, () -> orderService.createOrder(request));

        // This is the key proof that @Transactional rolled back ALL
        // inventory deductions made earlier in the loop, not just the
        // failing item.
        assertEquals(10, inventoryRepository.findByProductId(laptop.getId()).orElseThrow().getQuantity());
        assertEquals(5, inventoryRepository.findByProductId(keyboard.getId()).orElseThrow().getQuantity());
        assertEquals(0, inventoryRepository.findByProductId(mouse.getId()).orElseThrow().getQuantity());
        assertTrue(orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).isEmpty());
    }

    @Test
    void cancellingAnOrderRestoresInventory() {
        CreateOrderRequest request = new CreateOrderRequest(customer.getId(), List.of(
                new OrderItemRequest(laptop.getId(), 3)));

        Order order = orderService.createOrder(request);
        assertEquals(7, inventoryRepository.findByProductId(laptop.getId()).orElseThrow().getQuantity());

        Order cancelled = orderService.cancelOrder(order.getId());

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(10, inventoryRepository.findByProductId(laptop.getId()).orElseThrow().getQuantity());
    }
}
