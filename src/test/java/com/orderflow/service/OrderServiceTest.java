package com.orderflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.dto.request.CreateOrderRequest;
import com.orderflow.dto.request.OrderItemRequest;
import com.orderflow.dto.response.OrderResponse;
import com.orderflow.entity.*;
import org.springframework.security.access.AccessDeniedException;
import com.orderflow.exception.InsufficientStockException;
import com.orderflow.exception.ResourceNotFoundException;
import com.orderflow.repository.OrderRepository;
import com.orderflow.repository.ProductRepository;
import com.orderflow.repository.UserRepository;
import com.orderflow.repository.WebhookOutboxRepository;
import com.orderflow.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebhookOutboxRepository webhookOutboxRepository;

    @Mock
    private ProductService productService;

    private OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User customerUser;
    private UserPrincipal customerPrincipal;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                productRepository,
                userRepository,
                webhookOutboxRepository,
                productService,
                objectMapper
        );

        customerUser = new User("alice", "alice@example.com", "pass", Role.ROLE_CUSTOMER);
        customerUser.setId(10L);

        customerPrincipal = new UserPrincipal(
                10L,
                "alice",
                "alice@example.com",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        testProduct = new Product("SKU-LAPTOP", "Laptop", "Fast laptop", new BigDecimal("1000.00"), 10, "Electronics");
        testProduct.setId(1L);
    }

    @Test
    void createOrder_Success_ReservesStockAndSavesOutbox() {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(1L, 2)),
                "123 Tech Lane"
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(customerUser));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.createOrder(customerPrincipal, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(testProduct.getStockQuantity()).isEqualTo(8);

        verify(productRepository).save(testProduct);
        verify(productService).evictProductCache(1L);
        verify(webhookOutboxRepository).save(any(WebhookOutbox.class));
    }

    @Test
    void createOrder_InsufficientStock_ThrowsInsufficientStockException() {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(1L, 15)),
                "123 Tech Lane"
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(customerUser));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        assertThatThrownBy(() -> orderService.createOrder(customerPrincipal, request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        assertThat(testProduct.getStockQuantity()).isEqualTo(10);
        verify(orderRepository, never()).save(any());
        verify(webhookOutboxRepository, never()).save(any());
    }

    @Test
    void cancelOrder_CustomerOwnPendingOrder_RestoresStockAndUpdatesStatus() {
        Order order = new Order("ORD-1234", customerUser, OrderStatus.PENDING, new BigDecimal("2000.00"), "123 Tech Lane");
        order.setId(100L);
        OrderItem item = new OrderItem(order, testProduct, "Laptop", new BigDecimal("1000.00"), 2, new BigDecimal("2000.00"));
        order.addItem(item);

        testProduct.setStockQuantity(8);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(100L, customerPrincipal);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(testProduct.getStockQuantity()).isEqualTo(10);
        verify(productRepository).save(testProduct);
        verify(webhookOutboxRepository).save(any(WebhookOutbox.class));
    }

    @Test
    void getOrderById_AnotherCustomer_ThrowsAccessDeniedException() {
        User otherUser = new User("bob", "bob@example.com", "pass", Role.ROLE_CUSTOMER);
        otherUser.setId(20L);

        Order order = new Order("ORD-OTHER", otherUser, OrderStatus.PENDING, new BigDecimal("100.00"), "Address");
        order.setId(200L);

        when(orderRepository.findWithItemsById(200L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(200L, customerPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have permission");
    }

    @Test
    void createOrder_ProductInactive_ThrowsBadRequestException() {
        testProduct.setActive(false);
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(1L, 1)),
                "123 Tech Lane"
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(customerUser));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        assertThatThrownBy(() -> orderService.createOrder(customerPrincipal, request))
                .isInstanceOf(com.orderflow.exception.BadRequestException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    void cancelOrder_AlreadyCancelled_ThrowsBadRequestException() {
        Order order = new Order("ORD-1234", customerUser, OrderStatus.CANCELLED, new BigDecimal("100.00"), "Address");
        order.setId(100L);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(100L, customerPrincipal))
                .isInstanceOf(com.orderflow.exception.BadRequestException.class)
                .hasMessageContaining("PENDING status");
    }

    @Test
    void cancelOrder_AlreadyDelivered_ThrowsBadRequestException() {
        Order order = new Order("ORD-1234", customerUser, OrderStatus.DELIVERED, new BigDecimal("100.00"), "Address");
        order.setId(100L);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(100L, customerPrincipal))
                .isInstanceOf(com.orderflow.exception.BadRequestException.class);
    }

    @Test
    void updateOrderStatus_AlreadyCancelled_ThrowsBadRequestException() {
        UserPrincipal managerPrincipal = new UserPrincipal(
                2L,
                "manager",
                "mgr@example.com",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );

        Order order = new Order("ORD-1234", customerUser, OrderStatus.CANCELLED, new BigDecimal("1000.00"), "Address");
        order.setId(100L);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(100L, OrderStatus.PAID, managerPrincipal))
                .isInstanceOf(com.orderflow.exception.BadRequestException.class)
                .hasMessageContaining("Cannot change status of an already cancelled order");
    }

    @Test
    void updateOrderStatus_DeliveredCannotBeReverted() {
        UserPrincipal managerPrincipal = new UserPrincipal(
                2L,
                "manager",
                "mgr@example.com",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );

        Order order = new Order("ORD-1234", customerUser, OrderStatus.DELIVERED, new BigDecimal("1000.00"), "Address");
        order.setId(100L);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(100L, OrderStatus.CONFIRMED, managerPrincipal))
                .isInstanceOf(com.orderflow.exception.BadRequestException.class)
                .hasMessageContaining("Cannot change status of an already delivered order");
    }

    @Test
    void updateOrderStatus_ToCancelled_RestoresStock() {
        UserPrincipal managerPrincipal = new UserPrincipal(
                2L,
                "manager",
                "mgr@example.com",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );

        Order order = new Order("ORD-1234", customerUser, OrderStatus.PAID, new BigDecimal("1000.00"), "Address");
        order.setId(100L);
        OrderItem item = new OrderItem(order, testProduct, "Laptop", new BigDecimal("1000.00"), 2, new BigDecimal("2000.00"));
        order.addItem(item);

        testProduct.setStockQuantity(5);

        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(100L, OrderStatus.CANCELLED, managerPrincipal);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(testProduct.getStockQuantity()).isEqualTo(7);
        verify(productRepository).save(testProduct);
    }
}
