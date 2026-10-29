package com.orderflow.repository;

import com.orderflow.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    Page<Product> findByActiveTrue(Pageable pageable);
    Page<Product> findByCategoryIgnoreCaseAndActiveTrue(String category, Pageable pageable);
}
