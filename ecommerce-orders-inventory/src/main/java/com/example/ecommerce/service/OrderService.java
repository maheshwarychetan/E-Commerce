package com.example.ecommerce.service;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.OrderItemRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.exception.InsufficientInventoryException;
import com.example.ecommerce.exception.InvalidOrderException;
import com.example.ecommerce.exception.OrderNotFoundException;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerService customerService;
    private final RequestValidator requestValidator;

    public OrderService(OrderRepository orderRepository,
                         ProductRepository productRepository,
                         InventoryRepository inventoryRepository,
                         CustomerService customerService,
                         RequestValidator requestValidator) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.customerService = customerService;
        this.requestValidator = requestValidator;
    }

    /**
     * Creates an order for a customer: validates the customer and every
     * requested product, checks and locks inventory, deducts stock, and
     * persists the Order and its OrderItems - all as one unit of work.
     *
     * TRANSACTION BOUNDARY RATIONALE
     * ------------------------------
     * @Transactional is placed here, at the SERVICE layer, rather than on
     * individual repository methods or in the console controller, because
     * the "unit of work" that must succeed or fail atomically spans several
     * separate repository calls: validating the customer, validating and
     * locking inventory for every line item, persisting the Order, the
     * OrderItems, and the Inventory updates. Putting @Transactional on a
     * single repository method would not help, since the atomicity
     * requirement is about the SEQUENCE of calls, not any one call in
     * isolation. Putting it on the controller would leak persistence
     * concerns into the console/UI layer, which should stay a thin
     * presentation layer.
     *
     * If any item in the loop below is missing, inactive, or short on
     * stock, a RuntimeException is thrown. Spring's transactional proxy
     * detects that (all our custom exceptions are unchecked) and marks the
     * transaction rollback-only, so nothing committed by this method -
     * not the Order, not any OrderItem, not any Inventory deduction made
     * earlier in the same loop for a DIFFERENT product - is written to the
     * database. This is what guarantees the "all or nothing" checkout
     * behavior required by the spec (see OrderIntegrationTest).
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        requestValidator.validate(request);

        Customer customer = customerService.getCustomerById(request.getCustomerId());

        BigDecimal total = BigDecimal.ZERO;
        Order order = new Order(customer, BigDecimal.ZERO);

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found with id: " + itemRequest.getProductId()));

            if (!product.isActive()) {
                throw new InvalidOrderException(
                        "Product '" + product.getName() + "' is not currently available for purchase");
            }

            // Acquire a row-level lock on this product's inventory row
            // BEFORE reading its quantity - see
            // InventoryRepository.findByProductIdForUpdate for the full
            // explanation of why this lock prevents overselling under
            // concurrent checkouts.
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(product.getId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "No inventory record found for product '" + product.getName() + "'"));

            int available = inventory.getAvailableQuantity();
            if (available < itemRequest.getQuantity()) {
                log.warn("Insufficient inventory attempt for product '{}': requested {}, available {}",
                        product.getName(), itemRequest.getQuantity(), available);
                throw new InsufficientInventoryException(product.getName(), itemRequest.getQuantity(), available);
            }

            // Deduct immediately within this single atomic transaction.
            // Because the whole checkout is one transaction and this
            // product's row is locked, no other transaction can observe
            // this intermediate state; if a LATER item in this same loop
            // fails, this deduction is rolled back along with everything
            // else committed so far.
            inventory.setQuantity(inventory.getQuantity() - itemRequest.getQuantity());
            inventoryRepository.save(inventory);

            OrderItem orderItem = new OrderItem(product, itemRequest.getQuantity(), product.getPrice());
            order.addItem(orderItem);
            total = total.add(orderItem.getSubtotal());
        }

        order.setTotalAmount(total);
        order.setStatus(OrderStatus.CONFIRMED);

        Order saved = orderRepository.save(order);
        log.info("Created order id {} for customer '{}' with total {}", saved.getId(), customer.getName(), total);
        return saved;
    }

    /**
     * Cancels a CONFIRMED order and restores inventory for every item.
     * Also @Transactional at the service layer for the same reason as
     * createOrder: restoring N inventory rows and flipping the order's
     * status must happen all-or-nothing.
     */
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Order " + orderId + " is already cancelled");
        }

        // Lock each affected inventory row before restoring stock, for the
        // same concurrency-safety reason locking is used during checkout.
        for (OrderItem item : order.getItems()) {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "No inventory record found for product '" + item.getProduct().getName() + "'"));
            inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        log.info("Cancelled order id {} and restored inventory for {} item(s)", orderId, order.getItems().size());
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrderHistoryForCustomer(Long customerId) {
        // Confirms the customer exists first, so the user gets a clean
        // "customer not found" message rather than a silently empty list.
        customerService.getCustomerById(customerId);
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
