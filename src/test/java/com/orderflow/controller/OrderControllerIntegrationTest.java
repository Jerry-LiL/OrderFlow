package com.orderflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.dto.request.CreateOrderRequest;
import com.orderflow.dto.request.OrderItemRequest;
import com.orderflow.dto.request.OrderStatusUpdateRequest;
import com.orderflow.entity.OrderStatus;
import com.orderflow.entity.Product;
import com.orderflow.entity.Role;
import com.orderflow.entity.User;
import com.orderflow.repository.OrderRepository;
import com.orderflow.repository.ProductRepository;
import com.orderflow.repository.UserRepository;
import com.orderflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String customerToken;
    private String managerToken;
    private Long productId;

    @BeforeEach
    void setUp() {
        User customer = userRepository.findByUsername("order_customer")
                .orElseGet(() -> userRepository.save(new User("order_customer", "order_cust@example.com", "pass", Role.ROLE_CUSTOMER)));
        customerToken = "Bearer " + jwtTokenProvider.generateToken(customer.getId(), customer.getUsername(), customer.getEmail(), "ROLE_CUSTOMER");

        User manager = userRepository.findByUsername("order_manager")
                .orElseGet(() -> userRepository.save(new User("order_manager", "order_mgr@example.com", "pass", Role.ROLE_MANAGER)));
        managerToken = "Bearer " + jwtTokenProvider.generateToken(manager.getId(), manager.getUsername(), manager.getEmail(), "ROLE_MANAGER");

        Product product = productRepository.findBySku("SKU-ORD-1")
                .orElseGet(() -> productRepository.save(new Product("SKU-ORD-1", "Coffee Maker", "Drip coffee machine", new BigDecimal("79.99"), 20, "Appliances")));
        productId = product.getId();
    }

    @Test
    void createAndManageOrderLifecycle() throws Exception {
        CreateOrderRequest createReq = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 2)),
                "456 Market St, Suite 200"
        );

        String createResponseBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").isString())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(159.98))
                .andExpect(jsonPath("$.items").isArray())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createResponseBody).get("id").asLong();

        // Query order by id
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.totalAmount").value(159.98));

        // Query orders with pagination and status filter
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", customerToken)
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        // Manager updates status to PAID
        OrderStatusUpdateRequest updateReq = new OrderStatusUpdateRequest(OrderStatus.PAID);
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void cancelOrder_RestoresStock() throws Exception {
        CreateOrderRequest createReq = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, 3)),
                "789 Oak Ave"
        );

        String responseBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseBody).get("id").asLong();

        // Customer cancels the order
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
