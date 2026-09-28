package com.example.ecommerce.config;

import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Runs once at startup and inserts sample products/customers only if the
 * database is empty, so restarting the app never creates duplicate seed
 * data. Ordered to run BEFORE the console menu (see ConsoleController's
 * @Order(2)).
 */
@Component
@Order(1)
public class SeedDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;

    public SeedDataLoader(ProductRepository productRepository,
                           InventoryRepository inventoryRepository,
                           CustomerRepository customerRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() == 0) {
            seedProduct("Laptop", "15-inch business laptop", new BigDecimal("75000"), "LAP-001", 10);
            seedProduct("Wireless Mouse", "Ergonomic wireless mouse", new BigDecimal("1500"), "MOU-001", 20);
            seedProduct("Mechanical Keyboard", "RGB mechanical keyboard", new BigDecimal("4500"), "KEY-001", 5);
            seedProduct("Monitor", "27-inch 1440p monitor", new BigDecimal("18000"), "MON-001", 8);
            log.info("Seeded sample products and inventory");
        } else {
            log.info("Products already exist; skipping product seed data");
        }

        if (customerRepository.count() == 0) {
            customerRepository.save(new Customer("Asha Rao", "asha.rao@example.com", "9876543210"));
            customerRepository.save(new Customer("Vikram Singh", "vikram.singh@example.com", "9123456780"));
            log.info("Seeded sample customers");
        } else {
            log.info("Customers already exist; skipping customer seed data");
        }
    }

    private void seedProduct(String name, String description, BigDecimal price, String sku, int stock) {
        Product product = new Product(name, description, price, sku);
        Product saved = productRepository.save(product);
        inventoryRepository.save(new Inventory(saved, stock));
    }
}
