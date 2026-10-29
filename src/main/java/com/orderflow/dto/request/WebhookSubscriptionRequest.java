package com.orderflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookSubscriptionRequest(
        @NotBlank(message = "URL is required")
        @Size(max = 500, message = "URL cannot exceed 500 characters")
        String url,

        @NotBlank(message = "Secret is required")
        @Size(min = 8, max = 128, message = "Secret must be between 8 and 128 characters")
        String secret,

        @NotBlank(message = "Event types are required (e.g. 'order.created,order.status_changed' or '*')")
        @Size(max = 255, message = "Event types cannot exceed 255 characters")
        String eventTypes
) {}
