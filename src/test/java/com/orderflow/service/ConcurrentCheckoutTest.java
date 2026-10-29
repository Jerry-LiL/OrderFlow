package com.orderflow.service;

import com.orderflow.dto.request.CreateOrderRequest;
import com.orderflow.dto.request.OrderItemRequest;
import com.orderflow.dto.response.OrderResponse;
import com.orderflow.entity.Product;
import com.orderflow.entity.Role;
import com.orderflow.entity.User;
import com.orderflow.exception.ConflictException;
import com.orderflow.exception.InsufficientStockException;
import com.orderflow.repository.ProductRepository;
import com.orderflow.repository.UserRepository;
import com.orderflow.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentCheckoutTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentCheckoutTest.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Verify optimistic locking (@Version) under 50 concurrent checkout threads with zero negative stock anomalies")
    void testConcurrentCheckoutUnderFiftyThreads() throws InterruptedException {
        int initialStock = 10;
        int totalThreads = 50;

        String uniqueSuffix = String.valueOf(System.nanoTime());
        User user = new User("concurrent_user_" + uniqueSuffix, "concurrent_" + uniqueSuffix + "@example.com", "hash", Role.ROLE_CUSTOMER);
        User savedUser = userRepository.save(user);
        UserPrincipal principal = UserPrincipal.create(savedUser);

        Product product = new Product(
                "SKU-CONCURRENT-FLASH-" + uniqueSuffix,
                "Flash Sale Gadget",
                "High demand item",
                new BigDecimal("49.99"),
                initialStock,
                "Electronics"
        );
        Product savedProduct = productRepository.save(product);
        Long productId = savedProduct.getId();

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailures = new AtomicInteger(0);
        AtomicInteger outOfStockFailures = new AtomicInteger(0);
        AtomicInteger otherFailures = new AtomicInteger(0);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // wait until all threads are ready
                    CreateOrderRequest request = new CreateOrderRequest(
                            List.of(new OrderItemRequest(productId, 1)),
                            "100 Concurrency Ave"
                    );
                    OrderResponse order = orderService.createOrder(principal, request);
                    if (order != null) {
                        successCount.incrementAndGet();
                    }
                } catch (OptimisticLockingFailureException | ConflictException e) {
                    optimisticLockFailures.incrementAndGet();
                } catch (InsufficientStockException e) {
                    outOfStockFailures.incrementAndGet();
                } catch (Throwable t) {
                    otherFailures.incrementAndGet();
                    exceptions.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startGate.countDown(); // release all 50 threads simultaneously
        boolean finishedInTime = endGate.await(30, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startTime;
        executor.shutdown();

        assertThat(finishedInTime).isTrue();

        Product finalProduct = productRepository.findById(productId).orElseThrow();

        log.info("Concurrent checkout results under {} threads in {} ms:", totalThreads, durationMs);
        log.info("  Initial stock: {}", initialStock);
        log.info("  Successful orders: {}", successCount.get());
        log.info("  Optimistic lock conflicts: {}", optimisticLockFailures.get());
        log.info("  Out of stock rejections: {}", outOfStockFailures.get());
        log.info("  Other failures: {}", otherFailures.get());
        log.info("  Final stock quantity: {}", finalProduct.getStockQuantity());

        // Invariants:
        // 1. Stock quantity must never be negative
        assertThat(finalProduct.getStockQuantity())
                .as("Stock quantity must never drop below 0")
                .isGreaterThanOrEqualTo(0);

        // 2. Successful checkouts must not exceed initial inventory
        assertThat(successCount.get())
                .as("Successful orders cannot exceed initial stock")
                .isLessThanOrEqualTo(initialStock);

        // 3. Exactly: initialStock == remainingStock + successCount
        assertThat(finalProduct.getStockQuantity() + successCount.get())
                .as("Total items accounted for (remaining + sold) must equal initial stock")
                .isEqualTo(initialStock);

        // 4. All 50 requests are accounted for
        int totalAccounted = successCount.get() + optimisticLockFailures.get() + outOfStockFailures.get() + otherFailures.get();
        assertThat(totalAccounted).isEqualTo(totalThreads);
    }
}
