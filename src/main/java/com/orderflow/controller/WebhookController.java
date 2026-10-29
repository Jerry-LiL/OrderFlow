package com.orderflow.controller;

import com.orderflow.dto.request.WebhookSubscriptionRequest;
import com.orderflow.dto.response.WebhookDeliveryResponse;
import com.orderflow.dto.response.WebhookSubscriptionResponse;
import com.orderflow.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Webhooks", description = "Endpoints for managing webhook subscriptions and delivery outbox (ADMIN only)")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Register a new webhook subscription URL with HMAC secret")
    public ResponseEntity<WebhookSubscriptionResponse> createSubscription(
            @Valid @RequestBody WebhookSubscriptionRequest request
    ) {
        WebhookSubscriptionResponse response = webhookService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "List all registered webhook subscriptions")
    public ResponseEntity<List<WebhookSubscriptionResponse>> getSubscriptions() {
        List<WebhookSubscriptionResponse> list = webhookService.getSubscriptions();
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "Delete a webhook subscription")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        webhookService.deleteSubscription(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deliveries")
    @Operation(summary = "List recent webhook delivery attempts from the outbox table")
    public ResponseEntity<List<WebhookDeliveryResponse>> getDeliveries() {
        List<WebhookDeliveryResponse> deliveries = webhookService.getDeliveries();
        return ResponseEntity.ok(deliveries);
    }

    @PostMapping("/deliveries/{id}/retry")
    @Operation(summary = "Manually trigger retry for a failed webhook delivery")
    public ResponseEntity<WebhookDeliveryResponse> retryDelivery(@PathVariable Long id) {
        WebhookDeliveryResponse response = webhookService.retryDelivery(id);
        return ResponseEntity.ok(response);
    }
}
