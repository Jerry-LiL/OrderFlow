package com.orderflow.dto.response;

import com.orderflow.entity.WebhookSubscription;
import java.time.Instant;

public record WebhookSubscriptionResponse(
        Long id,
        String url,
        String eventTypes,
        boolean active,
        Instant createdAt
) {
    public static WebhookSubscriptionResponse fromEntity(WebhookSubscription sub) {
        return new WebhookSubscriptionResponse(
                sub.getId(),
                sub.getUrl(),
                sub.getEventTypes(),
                sub.isActive(),
                sub.getCreatedAt()
        );
    }
}
