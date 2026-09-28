package com.example.ecommerce.controller;

import com.example.ecommerce.dto.CreateOrderRequest;
import com.example.ecommerce.dto.CreateProductRequest;
import com.example.ecommerce.dto.OrderItemRequest;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.GlobalExceptionHandler;
import com.example.ecommerce.service.CustomerService;
import com.example.ecommerce.service.InventoryService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ProductService;
import com.example.ecommerce.util.ConsoleInputUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * The single entry point into the application from the terminal.
 *
 * This class deliberately contains NO business logic and NO repository
 * access: it only reads console input, delegates to the service layer, and
 * prints results (Console -> Service -> Repository -> PostgreSQL). Every
 * menu action is wrapped with GlobalExceptionHandler.handle(...) so a
 * business-rule violation is reported to the user and the menu loop
 * continues instead of crashing.
 */
@Component
@org.springframework.core.annotation.Order(2)
public class ConsoleController implements CommandLineRunner {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final ConsoleInputUtil input;

    public ConsoleController(ProductService productService,
                              InventoryService inventoryService,
                              CustomerService customerService,
                              OrderService orderService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.orderService = orderService;
        Scanner scanner = new Scanner(System.in);
        this.input = new ConsoleInputUtil(scanner);
    }

    @Override
    public void run(String... args) {
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = input.readLine("Choose an option: ");
            switch (choice) {
                case "1" -> productManagementMenu();
                case "2" -> inventoryManagementMenu();
                case "3" -> customerManagementMenu();
                case "4" -> GlobalExceptionHandler.handle(this::createOrderFlow);
                case "5" -> GlobalExceptionHandler.handle(this::viewOrderFlow);
                case "6" -> GlobalExceptionHandler.handle(this::cancelOrderFlow);
                case "7" -> GlobalExceptionHandler.handle(this::customerOrderHistoryFlow);
                case "8" -> GlobalExceptionHandler.handle(this::viewInventoryFlow);
                case "9" -> running = false;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
        System.out.println("Goodbye!");
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("        E-COMMERCE ORDER SYSTEM");
        System.out.println("========================================");
        System.out.println("1. Product Management");
        System.out.println("2. Inventory Management");
        System.out.println("3. Customer Management");
        System.out.println("4. Create Order");
        System.out.println("5. View Order");
        System.out.println("6. Cancel Order");
        System.out.println("7. Customer Order History");
        System.out.println("8. View Inventory");
        System.out.println("9. Exit");
    }

    // ---------------- Product Management ----------------

    private void productManagementMenu() {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Product Management ---");
            System.out.println("1. Create Product");
            System.out.println("2. View Products");
            System.out.println("3. Update Product");
            System.out.println("4. Back");
            String choice = input.readLine("Choose an option: ");
            switch (choice) {
                case "1" -> GlobalExceptionHandler.handle(this::createProductFlow);
                case "2" -> GlobalExceptionHandler.handle(this::viewProductsFlow);
                case "3" -> GlobalExceptionHandler.handle(this::updateProductFlow);
                case "4" -> back = true;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void createProductFlow() {
        String name = input.readLine("Product name: ");
        String description = input.readLine("Description: ");
        BigDecimal price = input.readBigDecimal("Price: ");
        String sku = input.readLine("SKU: ");

        Product product = productService.createProduct(new CreateProductRequest(name, description, price, sku));

        int initialStock = input.readInt("Initial stock quantity: ");
        inventoryService.initializeStock(product.getId(), initialStock);

        System.out.println("Product created successfully with ID: " + product.getId());
    }

    private void viewProductsFlow() {
        List<Product> products = productService.getAllProducts();
        if (products.isEmpty()) {
            System.out.println("No products found.");
            return;
        }
        System.out.println();
        System.out.printf("%-5s %-22s %-12s %-10s %-8s %-8s%n", "ID", "Name", "SKU", "Price", "Stock", "Active");
        for (Product p : products) {
            int stock;
            try {
                stock = inventoryService.getInventoryForProduct(p.getId()).getAvailableQuantity();
            } catch (Exception e) {
                stock = 0;
            }
            System.out.printf("%-5d %-22s %-12s %-10s %-8d %-8s%n",
                    p.getId(), p.getName(), p.getSku(), p.getPrice(), stock, p.isActive());
        }
    }

    private void updateProductFlow() {
        Long id = input.readLong("Product ID to update: ");
        Product existing = productService.getProductById(id);

        System.out.println("Leave blank to keep the current value.");
        String name = input.readLine("Name [" + existing.getName() + "]: ");
        String description = input.readLine("Description [" + existing.getDescription() + "]: ");
        String priceStr = input.readLine("Price [" + existing.getPrice() + "]: ");
        String activeStr = input.readLine("Active true/false [" + existing.isActive() + "]: ");
        String sku = input.readLine("SKU [" + existing.getSku() + "]: ");

        BigDecimal price = priceStr.isBlank() ? null : new BigDecimal(priceStr);
        Boolean active = activeStr.isBlank() ? null : Boolean.parseBoolean(activeStr);

        productService.updateProduct(
                id,
                name.isBlank() ? null : name,
                description.isBlank() ? null : description,
                price,
                active,
                sku.isBlank() ? null : sku);

        System.out.println("Product updated successfully.");
    }

    // ---------------- Inventory Management ----------------

    private void inventoryManagementMenu() {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Inventory Management ---");
            System.out.println("1. Add Stock");
            System.out.println("2. View Inventory");
            System.out.println("3. Back");
            String choice = input.readLine("Choose an option: ");
            switch (choice) {
                case "1" -> GlobalExceptionHandler.handle(this::addStockFlow);
                case "2" -> GlobalExceptionHandler.handle(this::viewInventoryFlow);
                case "3" -> back = true;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void addStockFlow() {
        Long productId = input.readLong("Product ID: ");
        int quantity = input.readInt("Quantity to add: ");
        Inventory inventory = inventoryService.addStock(productId, quantity);
        System.out.println("Stock updated. New total quantity: " + inventory.getQuantity());
    }

    private void viewInventoryFlow() {
        List<Inventory> all = inventoryService.getAllInventory();
        if (all.isEmpty()) {
            System.out.println("No inventory records found.");
            return;
        }
        System.out.println();
        System.out.printf("%-10s %-22s %-12s %-8s %-8s %-10s%n",
                "ProdID", "Name", "SKU", "Total", "Reserved", "Available");
        for (Inventory inv : all) {
            Product p = inv.getProduct();
            System.out.printf("%-10d %-22s %-12s %-8d %-8d %-10d%n",
                    p.getId(), p.getName(), p.getSku(),
                    inv.getQuantity(), inv.getReservedQuantity(), inv.getAvailableQuantity());
        }
    }

    // ---------------- Customer Management ----------------

    private void customerManagementMenu() {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Customer Management ---");
            System.out.println("1. Create Customer");
            System.out.println("2. View Customer");
            System.out.println("3. Back");
            String choice = input.readLine("Choose an option: ");
            switch (choice) {
                case "1" -> GlobalExceptionHandler.handle(this::createCustomerFlow);
                case "2" -> GlobalExceptionHandler.handle(this::viewCustomerFlow);
                case "3" -> back = true;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void createCustomerFlow() {
        String name = input.readLine("Customer name: ");
        String email = input.readLine("Email: ");
        String phone = input.readLine("Phone: ");
        Customer customer = customerService.createCustomer(name, email, phone);
        System.out.println("Customer created successfully with ID: " + customer.getId());
    }

    private void viewCustomerFlow() {
        Long id = input.readLong("Customer ID: ");
        Customer customer = customerService.getCustomerById(id);
        System.out.println();
        System.out.println("ID: " + customer.getId());
        System.out.println("Name: " + customer.getName());
        System.out.println("Email: " + customer.getEmail());
        System.out.println("Phone: " + customer.getPhone());
        System.out.println("Created At: " + customer.getCreatedAt());
    }

    // ---------------- Orders ----------------

    private void createOrderFlow() {
        Long customerId = input.readLong("Customer ID: ");
        List<OrderItemRequest> items = new ArrayList<>();

        boolean addingItems = true;
        while (addingItems) {
            Long productId = input.readLong("Product ID (enter 0 to finish adding items): ");
            if (productId == 0) {
                addingItems = false;
                continue;
            }
            int quantity = input.readInt("Quantity: ");
            items.add(new OrderItemRequest(productId, quantity));
        }

        if (items.isEmpty()) {
            System.out.println("No items added. Order cancelled.");
            return;
        }

        Order order = orderService.createOrder(new CreateOrderRequest(customerId, items));
        System.out.println("Order created successfully! Order ID: " + order.getId()
                + ", Total: " + order.getTotalAmount());
    }

    private void viewOrderFlow() {
        Long orderId = input.readLong("Order ID: ");
        Order order = orderService.getOrderById(orderId);
        printOrderDetails(order);
    }

    private void cancelOrderFlow() {
        Long orderId = input.readLong("Order ID to cancel: ");
        Order order = orderService.cancelOrder(orderId);
        System.out.println("Order " + order.getId() + " has been cancelled and inventory restored.");
    }

    private void customerOrderHistoryFlow() {
        Long customerId = input.readLong("Customer ID: ");
        List<Order> orders = orderService.getOrderHistoryForCustomer(customerId);
        if (orders.isEmpty()) {
            System.out.println("This customer has no orders.");
            return;
        }
        System.out.println();
        System.out.printf("%-8s %-22s %-12s %-10s%n", "OrderID", "Date", "Status", "Total");
        for (Order o : orders) {
            System.out.printf("%-8d %-22s %-12s %-10s%n",
                    o.getId(), o.getCreatedAt(), o.getStatus(), o.getTotalAmount());
        }

        String view = input.readLine("Enter an Order ID to view full details, or press Enter to go back: ");
        if (!view.isBlank()) {
            try {
                Order selected = orderService.getOrderById(Long.parseLong(view));
                printOrderDetails(selected);
            } catch (NumberFormatException e) {
                System.out.println("Invalid Order ID.");
            }
        }
    }

    private void printOrderDetails(Order order) {
        System.out.println();
        System.out.println("Order ID: " + order.getId());
        System.out.println("Customer: " + order.getCustomer().getName());
        System.out.println("Status: " + order.getStatus());
        System.out.println("Created At: " + order.getCreatedAt());
        System.out.println();
        System.out.printf("%-22s %-12s %-6s %-10s %-10s%n", "Product", "SKU", "Qty", "UnitPrice", "Subtotal");
        for (OrderItem item : order.getItems()) {
            System.out.printf("%-22s %-12s %-6d %-10s %-10s%n",
                    item.getProduct().getName(), item.getProduct().getSku(),
                    item.getQuantity(), item.getUnitPrice(), item.getSubtotal());
        }
        System.out.println();
        System.out.println("Total Amount: " + order.getTotalAmount());
    }
}
