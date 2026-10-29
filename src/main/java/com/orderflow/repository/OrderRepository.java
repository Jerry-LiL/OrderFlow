package com.orderflow.repository;

import com.orderflow.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findWithItemsByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
