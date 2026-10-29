package com.orderflow.specification;

import com.orderflow.entity.Order;
import com.orderflow.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSpecificationTest {

    @Test
    void filter_ReturnsNonNullSpecification() {
        Specification<Order> spec = OrderSpecification.filter(
                1L,
                OrderStatus.CONFIRMED,
                Instant.now().minusSeconds(3600),
                Instant.now(),
                new BigDecimal("10.00"),
                new BigDecimal("500.00"),
                "ORD-123"
        );

        assertThat(spec).isNotNull();
    }

    @Test
    void filter_WithAllNulls_ReturnsNonNullSpecification() {
        Specification<Order> spec = OrderSpecification.filter(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(spec).isNotNull();
    }
}
