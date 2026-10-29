package com.orderflow.service;

import com.orderflow.dto.request.ProductRequest;
import com.orderflow.dto.response.ProductResponse;
import com.orderflow.entity.Product;
import com.orderflow.exception.BadRequestException;
import com.orderflow.exception.ConflictException;
import com.orderflow.exception.ResourceNotFoundException;
import com.orderflow.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(String category, Pageable pageable) {
        Page<Product> page;
        if (category != null && !category.isBlank()) {
            page = productRepository.findByCategoryIgnoreCaseAndActiveTrue(category.trim(), pageable);
        } else {
            page = productRepository.findByActiveTrue(pageable);
        }
        return page.map(ProductResponse::fromEntity);
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product from database for id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product with SKU already exists: " + request.sku());
        }

        Product product = new Product(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.category()
        );
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product with SKU already exists: " + request.sku());
        }

        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setCategory(request.category());

        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateStock(Long id, Integer delta) {
        if (delta == null || delta == 0) {
            throw new BadRequestException("Delta must not be null or zero");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        int newStock = product.getStockQuantity() + delta;
        if (newStock < 0) {
            throw new BadRequestException("Stock cannot be reduced below zero. Current stock: " + product.getStockQuantity() + ", requested reduction: " + Math.abs(delta));
        }

        product.setStockQuantity(newStock);
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setActive(false);
        productRepository.save(product);
    }

    @CacheEvict(value = "products", key = "#id")
    public void evictProductCache(Long id) {
        log.debug("Evicting product cache for id: {}", id);
    }
}
