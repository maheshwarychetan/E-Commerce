package com.example.ecommerce.service;

import com.example.ecommerce.dto.CreateProductRequest;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.RequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        productService = new ProductService(productRepository, new RequestValidator(validator));
    }

    @Test
    void createsProductSuccessfully() {
        CreateProductRequest request = new CreateProductRequest("Laptop", "A laptop", new BigDecimal("75000"), "LAP-001");
        when(productRepository.existsBySku("LAP-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = productService.createProduct(request);

        assertEquals("Laptop", created.getName());
        assertEquals("LAP-001", created.getSku());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void rejectsDuplicateSku() {
        CreateProductRequest request = new CreateProductRequest("Laptop", "A laptop", new BigDecimal("75000"), "LAP-001");
        when(productRepository.existsBySku("LAP-001")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidPrice() {
        CreateProductRequest request = new CreateProductRequest("Laptop", "A laptop", new BigDecimal("-5"), "LAP-002");

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }
}
