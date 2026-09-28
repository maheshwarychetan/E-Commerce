package com.example.ecommerce.service;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.OrderItemRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.CustomerNotFoundException;
import com.example.ecommerce.exception.InsufficientInventoryException;
import com.example.ecommerce.exception.InvalidOrderException;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.RequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private InventoryRepository inventoryRepository;
    private CustomerService customerService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        customerService = mock(CustomerService.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        orderService = new OrderService(orderRepository, productRepository, inventoryRepository,
                customerService, new RequestValidator(validator));
    }

    private Product activeProduct(long id, String name, String sku, String price) {
        Product product = new Product(name, "desc", new BigDecimal(price), sku);
        setId(product, id);
        return product;
    }

    private void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createsOrderSuccessfullyAndCalculatesTotal() {
        Customer customer = new Customer("Asha", "asha@example.com", "123");
        Product laptop = activeProduct(1L, "Laptop", "LAP-001", "75000");
        Inventory inventory = new Inventory(laptop, 10);

        when(customerService.getCustomerById(1L)).thenReturn(customer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 2)));
        Order order = orderService.createOrder(request);

        assertEquals(new BigDecimal("150000"), order.getTotalAmount());
        assertEquals(8, inventory.getQuantity());
    }

    @Test
    void rejectsInsufficientInventory() {
        Customer customer = new Customer("Asha", "asha@example.com", "123");
        Product laptop = activeProduct(1L, "Laptop", "LAP-001", "75000");
        Inventory inventory = new Inventory(laptop, 1);

        when(customerService.getCustomerById(1L)).thenReturn(customer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        CreateOrderRequest request = new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 5)));

        assertThrows(InsufficientInventoryException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidCustomer() {
        when(customerService.getCustomerById(99L))
                .thenThrow(new CustomerNotFoundException("Customer not found with id: 99"));

        CreateOrderRequest request = new CreateOrderRequest(99L, List.of(new OrderItemRequest(1L, 1)));

        assertThrows(CustomerNotFoundException.class, () -> orderService.createOrder(request));
    }

    @Test
    void rejectsInvalidProduct() {
        Customer customer = new Customer("Asha", "asha@example.com", "123");
        when(customerService.getCustomerById(1L)).thenReturn(customer);
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(1L, List.of(new OrderItemRequest(42L, 1)));

        assertThrows(ProductNotFoundException.class, () -> orderService.createOrder(request));
    }

    @Test
    void rejectsInactiveProduct() {
        Customer customer = new Customer("Asha", "asha@example.com", "123");
        Product laptop = activeProduct(1L, "Laptop", "LAP-001", "75000");
        laptop.setActive(false);

        when(customerService.getCustomerById(1L)).thenReturn(customer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));

        CreateOrderRequest request = new CreateOrderRequest(1L, List.of(new OrderItemRequest(1L, 1)));

        assertThrows(InvalidOrderException.class, () -> orderService.createOrder(request));
    }

    @Test
    void multiItemOrderFailsWhenOneItemLacksStock() {
        // Laptop has stock, Keyboard has stock, Mouse does NOT - matches the
        // exact scenario described in the project spec.
        Customer customer = new Customer("Asha", "asha@example.com", "123");
        Product laptop = activeProduct(1L, "Laptop", "LAP-001", "75000");
        Product keyboard = activeProduct(2L, "Keyboard", "KEY-001", "4500");
        Product mouse = activeProduct(3L, "Mouse", "MOU-001", "1500");

        Inventory laptopInv = new Inventory(laptop, 10);
        Inventory keyboardInv = new Inventory(keyboard, 5);
        Inventory mouseInv = new Inventory(mouse, 0);

        when(customerService.getCustomerById(1L)).thenReturn(customer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(productRepository.findById(2L)).thenReturn(Optional.of(keyboard));
        when(productRepository.findById(3L)).thenReturn(Optional.of(mouse));
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(laptopInv));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(keyboardInv));
        when(inventoryRepository.findByProductIdForUpdate(3L)).thenReturn(Optional.of(mouseInv));

        CreateOrderRequest request = new CreateOrderRequest(1L, List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(2L, 1),
                new OrderItemRequest(3L, 1)));

        assertThrows(InsufficientInventoryException.class, () -> orderService.createOrder(request));

        // The order must never be persisted. Note: this unit test (with
        // mocked repositories) only proves the service never calls save().
        // Full DB-level rollback of the earlier Laptop/Keyboard inventory
        // mutations is guaranteed by the real @Transactional boundary and is
        // verified against a real PostgreSQL instance in
        // OrderIntegrationTest.multiItemOrderRollsBackCompletelyWhenOneItemIsShort.
        verify(orderRepository, never()).save(any());
    }
}
