package com.example.ecommerce.service;

import com.example.ecommerce.dto.CreateProductRequest;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.ProductNotFoundException;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final RequestValidator requestValidator;

    public ProductService(ProductRepository productRepository, RequestValidator requestValidator) {
        this.productRepository = productRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        requestValidator.validate(request);

        // Uniqueness cannot be expressed as a bean-validation annotation
        // (it depends on what's already in the database), so it is checked
        // here explicitly, in addition to the DB-level unique constraint on
        // product.sku which protects against races on this same check.
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("A product with SKU '" + request.getSku() + "' already exists");
        }

        Product product = new Product(request.getName(), request.getDescription(), request.getPrice(), request.getSku());
        Product saved = productRepository.save(product);
        log.info("Created product '{}' (SKU: {})", saved.getName(), saved.getSku());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    @Transactional
    public Product updateProduct(Long id, String name, String description, BigDecimal price, Boolean active, String newSku) {
        Product product = getProductById(id);

        if (newSku != null && !newSku.isBlank() && !newSku.equals(product.getSku())) {
            if (productRepository.existsBySku(newSku)) {
                throw new IllegalArgumentException("A product with SKU '" + newSku + "' already exists");
            }
            product.setSku(newSku);
        }
        if (name != null && !name.isBlank()) {
            product.setName(name);
        }
        if (description != null) {
            product.setDescription(description);
        }
        if (price != null) {
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price must be greater than zero");
            }
            product.setPrice(price);
        }
        if (active != null) {
            product.setActive(active);
        }

        Product updated = productRepository.save(product);
        log.info("Updated product id {}", updated.getId());
        return updated;
    }
}
