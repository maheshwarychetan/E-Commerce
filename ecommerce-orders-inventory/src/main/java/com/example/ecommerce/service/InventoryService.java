package com.example.ecommerce.service;

import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryRepository inventoryRepository, ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
    }

    /**
     * Creates (or tops up) the inventory row for a freshly created product.
     * Not lock-sensitive: this only ever runs once, immediately after a
     * brand-new product is created, before any order could reference it.
     */
    @Transactional
    public Inventory initializeStock(Long productId, int initialQuantity) {
        if (initialQuantity < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> new Inventory(product, 0));
        inventory.setQuantity(inventory.getQuantity() + initialQuantity);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Initialized/added {} units of stock for product id {}", initialQuantity, productId);
        return saved;
    }

    @Transactional
    public Inventory addStock(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be greater than zero");
        }
        // Locked for the same reason checkout locks the row: adding stock
        // must not race with a concurrent checkout reading/updating the
        // same inventory row.
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException("No inventory record found for product id: " + productId));
        inventory.setQuantity(inventory.getQuantity() + quantity);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Added {} units to product id {}. New quantity: {}", quantity, productId, saved.getQuantity());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Inventory getInventoryForProduct(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductNotFoundException("No inventory record found for product id: " + productId));
    }
}
