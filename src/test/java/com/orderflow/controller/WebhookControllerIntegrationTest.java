package com.orderflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.dto.request.WebhookSubscriptionRequest;
import com.orderflow.entity.OutboxStatus;
import com.orderflow.entity.WebhookOutbox;
import com.orderflow.repository.WebhookOutboxRepository;
import com.orderflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookOutboxRepository outboxRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtTokenProvider.generateToken(1L, "admin_wh", "admin_wh@orderflow.local", "ROLE_ADMIN");
        customerToken = "Bearer " + jwtTokenProvider.generateToken(2L, "cust_wh", "cust_wh@orderflow.local", "ROLE_CUSTOMER");
    }

    @Test
    void webhookSubscriptionLifecycle_AdminOnly() throws Exception {
        // Customer forbidden
        WebhookSubscriptionRequest subReq = new WebhookSubscriptionRequest(
                "https://partner.example.com/webhook",
                "whsec_supersecret12345",
                "order.created,order.status_changed"
        );

        mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isForbidden());

        // Admin success
        String createResponse = mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://partner.example.com/webhook"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Long subId = objectMapper.readTree(createResponse).get("id").asLong();

        // List subscriptions
        mockMvc.perform(get("/api/v1/webhooks/subscriptions")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Delete subscription
        mockMvc.perform(delete("/api/v1/webhooks/subscriptions/" + subId)
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void retryDelivery_Success() throws Exception {
        WebhookOutbox outbox = new WebhookOutbox("order.created", "ORD-FAIL-1", "{}");
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setAttempts(2);
        outbox.setLastError("Connection timed out");
        WebhookOutbox saved = outboxRepository.save(outbox);

        mockMvc.perform(post("/api/v1/webhooks/deliveries/" + saved.getId() + "/retry")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
    }
}
