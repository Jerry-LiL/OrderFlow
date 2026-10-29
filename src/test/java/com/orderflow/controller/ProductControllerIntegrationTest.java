package com.orderflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.dto.request.ProductRequest;
import com.orderflow.dto.request.StockUpdateRequest;
import com.orderflow.entity.Product;
import com.orderflow.repository.ProductRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtTokenProvider.generateToken(1L, "admin", "admin@orderflow.local", "ROLE_ADMIN");
        customerToken = "Bearer " + jwtTokenProvider.generateToken(2L, "cust", "cust@orderflow.local", "ROLE_CUSTOMER");
    }

    @Test
    void getProducts_PublicAccess() throws Exception {
        Product p = new Product("TEST-SKU-1", "Desk Lamp", "LED lamp", new BigDecimal("45.00"), 50, "Lighting");
        productRepository.save(p);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createProduct_AsAdmin_Success() throws Exception {
        ProductRequest req = new ProductRequest("SKU-USB-C", "USB-C Hub", "Multi-port adapter", new BigDecimal("29.99"), 100, "Accessories");

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-USB-C"))
                .andExpect(jsonPath("$.price").value(29.99));
    }

    @Test
    void createProduct_AsCustomer_Forbidden() throws Exception {
        ProductRequest req = new ProductRequest("SKU-HACK", "Hacked Item", "desc", new BigDecimal("1.00"), 1, "Misc");

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStock_AsAdmin_Success() throws Exception {
        Product p = new Product("SKU-STOCK-TEST", "Monitor Arm", "Heavy duty", new BigDecimal("89.00"), 20, "Office");
        Product saved = productRepository.save(p);

        StockUpdateRequest updateReq = new StockUpdateRequest(5);

        mockMvc.perform(patch("/api/v1/products/" + saved.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(25));
    }
}
