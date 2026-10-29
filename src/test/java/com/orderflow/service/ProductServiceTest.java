package com.orderflow.service;

import com.orderflow.dto.request.ProductRequest;
import com.orderflow.dto.response.ProductResponse;
import com.orderflow.entity.Product;
import com.orderflow.exception.BadRequestException;
import com.orderflow.exception.ConflictException;
import com.orderflow.exception.ResourceNotFoundException;
import com.orderflow.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void getProducts_WithoutCategory_ReturnsAllActive() {
        Product p1 = new Product("SKU-1", "Mouse", "A mouse", new BigDecimal("25.00"), 50, "Accessories");
        p1.setId(1L);
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByActiveTrue(pageable)).thenReturn(new PageImpl<>(List.of(p1)));

        Page<ProductResponse> result = productService.getProducts(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).sku()).isEqualTo("SKU-1");
    }

    @Test
    void getProducts_WithCategory_ReturnsFiltered() {
        Product p1 = new Product("SKU-1", "Mouse", "A mouse", new BigDecimal("25.00"), 50, "Accessories");
        p1.setId(1L);
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByCategoryIgnoreCaseAndActiveTrue("Accessories", pageable))
                .thenReturn(new PageImpl<>(List.of(p1)));

        Page<ProductResponse> result = productService.getProducts("Accessories", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).category()).isEqualTo("Accessories");
    }

    @Test
    void getProductById_Found_ReturnsProductResponse() {
        Product p = new Product("SKU-1", "Mouse", "A mouse", new BigDecimal("25.00"), 50, "Accessories");
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        ProductResponse response = productService.getProductById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Mouse");
    }

    @Test
    void getProductById_NotFound_ThrowsResourceNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found with id: 99");
    }

    @Test
    void createProduct_Success() {
        ProductRequest request = new ProductRequest("SKU-NEW", "Monitor", "4K", new BigDecimal("399.00"), 15, "Electronics");
        when(productRepository.existsBySku("SKU-NEW")).thenReturn(false);

        Product saved = new Product("SKU-NEW", "Monitor", "4K", new BigDecimal("399.00"), 15, "Electronics");
        saved.setId(10L);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductResponse response = productService.createProduct(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("SKU-NEW");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_DuplicateSku_ThrowsConflictException() {
        ProductRequest request = new ProductRequest("SKU-EXISTING", "Monitor", "4K", new BigDecimal("399.00"), 15, "Electronics");
        when(productRepository.existsBySku("SKU-EXISTING")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product with SKU already exists");
    }

    @Test
    void updateStock_PositiveDelta_IncreasesStock() {
        Product product = new Product("SKU-1", "Mouse", "A mouse", new BigDecimal("25.00"), 20, "Accessories");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateStock(1L, 10);

        assertThat(response.stockQuantity()).isEqualTo(30);
    }

    @Test
    void updateStock_NegativeDeltaWithinLimit_DecreasesStock() {
        Product product = new Product("SKU-1", "Mouse", "A mouse", new BigDecimal("25.00"), 20, "Accessories");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateStock(1L, -5);

        assertThat(response.stockQuantity()).isEqualTo(15);
    }

    @Test
    void updateStock_ExcessiveNegativeDelta_ThrowsBadRequestException() {
        Product product = new Product("SKU-1", "Mouse", "A mouse", new BigDecimal("25.00"), 5, "Accessories");
        product.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateStock(1L, -10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Stock cannot be reduced below zero");

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_Success() {
        Product existing = new Product("SKU-OLD", "Old Name", "Old Desc", new BigDecimal("10.00"), 5, "Cat");
        existing.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest updateReq = new ProductRequest("SKU-OLD", "New Name", "New Desc", new BigDecimal("15.00"), 10, "NewCat");
        ProductResponse response = productService.updateProduct(1L, updateReq);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(response.stockQuantity()).isEqualTo(10);
    }

    @Test
    void updateProduct_DuplicateSku_ThrowsConflictException() {
        Product existing = new Product("SKU-OLD", "Old Name", "Old Desc", new BigDecimal("10.00"), 5, "Cat");
        existing.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySku("SKU-DIFF")).thenReturn(true);

        ProductRequest updateReq = new ProductRequest("SKU-DIFF", "New Name", "New Desc", new BigDecimal("15.00"), 10, "NewCat");

        assertThatThrownBy(() -> productService.updateProduct(1L, updateReq))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product with SKU already exists");
    }

    @Test
    void deleteProduct_SoftDeletes() {
        Product existing = new Product("SKU-DEL", "To Delete", "Desc", new BigDecimal("10.00"), 5, "Cat");
        existing.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.deleteProduct(1L);

        assertThat(existing.isActive()).isFalse();
        verify(productRepository).save(existing);
    }

    @Test
    void evictProductCache_ExecutesWithoutError() {
        productService.evictProductCache(1L);
    }
}
