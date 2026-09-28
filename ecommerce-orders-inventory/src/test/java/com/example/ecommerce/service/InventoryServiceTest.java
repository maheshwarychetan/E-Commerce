package com.example.ecommerce.service;

import com.example.ecommerce.entity.Inventory;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private ProductRepository productRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        productRepository = mock(ProductRepository.class);
        inventoryService = new InventoryService(inventoryRepository, productRepository);
    }

    @Test
    void addsStockSuccessfully() {
        Product product = new Product("Mouse", "desc", new BigDecimal("1500"), "MOU-001");
        Inventory inventory = new Inventory(product, 10);

        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        Inventory updated = inventoryService.addStock(1L, 5);

        assertEquals(15, updated.getQuantity());
    }

    @Test
    void rejectsNegativeOrZeroStock() {
        assertThrows(IllegalArgumentException.class, () -> inventoryService.addStock(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.addStock(1L, -3));
        verify(inventoryRepository, never()).findByProductIdForUpdate(anyLong());
    }

    @Test
    void calculatesAvailableQuantityCorrectly() {
        Product product = new Product("Keyboard", "desc", new BigDecimal("4500"), "KEY-001");
        Inventory inventory = new Inventory(product, 10);
        inventory.setReservedQuantity(3);

        assertEquals(7, inventory.getAvailableQuantity());
    }
}
