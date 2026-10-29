package com.orderflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.dto.request.CreateOrderRequest;
import com.orderflow.dto.request.OrderItemRequest;
import com.orderflow.dto.response.OrderResponse;
import com.orderflow.dto.response.PageResponse;
import com.orderflow.entity.*;
import org.springframework.security.access.AccessDeniedException;
import com.orderflow.exception.BadRequestException;
import com.orderflow.exception.InsufficientStockException;
import com.orderflow.exception.ResourceNotFoundException;
import com.orderflow.repository.OrderRepository;
import com.orderflow.repository.ProductRepository;
import com.orderflow.repository.UserRepository;
import com.orderflow.repository.WebhookOutboxRepository;
import com.orderflow.security.UserPrincipal;
import com.orderflow.specification.OrderSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WebhookOutboxRepository webhookOutboxRepository;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            WebhookOutboxRepository webhookOutboxRepository,
            ProductService productService,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.webhookOutboxRepository = webhookOutboxRepository;
        this.productService = productService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(UserPrincipal userPrincipal, CreateOrderRequest request) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userPrincipal.getId()));

        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Order order = new Order(orderNumber, user, OrderStatus.PENDING, BigDecimal.ZERO, request.shippingAddress());

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemRequest.productId()));

            if (!product.isActive()) {
                throw new BadRequestException("Product " + product.getSku() + " is no longer active");
            }

            if (product.getStockQuantity() < itemRequest.quantity()) {
                throw new InsufficientStockException("Insufficient stock for product " + product.getSku() +
                        ": available " + product.getStockQuantity() + ", requested " + itemRequest.quantity());
            }

            // Reserve stock (optimistic locking will detect concurrent updates via @Version)
            product.setStockQuantity(product.getStockQuantity() - itemRequest.quantity());
            productRepository.save(product);
            productService.evictProductCache(product.getId());

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(subtotal);

            OrderItem orderItem = new OrderItem(
                    order,
                    product,
                    product.getName(),
                    product.getPrice(),
                    itemRequest.quantity(),
                    subtotal
            );
            order.addItem(orderItem);
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);

        recordOutboxEvent("order.created", savedOrder.getOrderNumber(), OrderResponse.fromEntity(savedOrder));

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, UserPrincipal userPrincipal) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        validateOrderOwnership(order, userPrincipal);
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(
            UserPrincipal userPrincipal,
            OrderStatus status,
            Instant startDate,
            Instant endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String orderNumber,
            Pageable pageable
    ) {
        Long filterUserId = null;
        boolean isCustomer = userPrincipal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_CUSTOMER.name()));

        if (isCustomer) {
            filterUserId = userPrincipal.getId();
        }

        Specification<Order> spec = OrderSpecification.filter(
                filterUserId,
                status,
                startDate,
                endDate,
                minAmount,
                maxAmount,
                orderNumber
        );

        Page<Order> page = orderRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(OrderResponse::fromEntity));
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus, UserPrincipal userPrincipal) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot change status of an already cancelled order");
        }
        if (currentStatus == OrderStatus.DELIVERED && newStatus != OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot change status of an already delivered order");
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStockForOrder(order);
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);

        recordOutboxEvent("order.status_changed", updated.getOrderNumber(), OrderResponse.fromEntity(updated));

        return OrderResponse.fromEntity(updated);
    }

    @Transactional
    public OrderResponse cancelOrder(Long id, UserPrincipal userPrincipal) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        boolean isCustomer = userPrincipal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_CUSTOMER.name()));

        if (isCustomer) {
            validateOrderOwnership(order, userPrincipal);
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new BadRequestException("Customers can only cancel orders in PENDING status. Current status: " + order.getStatus());
            }
        } else {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new BadRequestException("Order is already cancelled");
            }
            if (order.getStatus() == OrderStatus.DELIVERED) {
                throw new BadRequestException("Delivered orders cannot be cancelled");
            }
        }

        restoreStockForOrder(order);
        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);

        recordOutboxEvent("order.cancelled", updated.getOrderNumber(), OrderResponse.fromEntity(updated));

        return OrderResponse.fromEntity(updated);
    }

    private void restoreStockForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
            productService.evictProductCache(product.getId());
        }
    }

    private void validateOrderOwnership(Order order, UserPrincipal userPrincipal) {
        boolean isCustomer = userPrincipal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_CUSTOMER.name()));

        if (isCustomer && !order.getUser().getId().equals(userPrincipal.getId())) {
            throw new AccessDeniedException("You do not have permission to access this order");
        }
    }

    private void recordOutboxEvent(String eventType, String aggregateId, Object payloadObj) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadObj);
            WebhookOutbox outbox = new WebhookOutbox(eventType, aggregateId, jsonPayload);
            webhookOutboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook outbox payload for event {}: {}", eventType, e.getMessage());
        }
    }
}
