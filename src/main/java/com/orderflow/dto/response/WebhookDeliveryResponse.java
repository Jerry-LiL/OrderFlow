package com.orderflow.dto.response;

import com.orderflow.entity.OutboxStatus;
import com.orderflow.entity.WebhookOutbox;
import java.time.Instant;

public record WebhookDeliveryResponse(
        Long id,
        String eventType,
        String aggregateId,
        String payload,
        OutboxStatus status,
        int attempts,
        int maxAttempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static WebhookDeliveryResponse fromEntity(WebhookOutbox outbox) {
        return new WebhookDeliveryResponse(
                outbox.getId(),
                outbox.getEventType(),
                outbox.getAggregateId(),
                outbox.getPayload(),
                outbox.getStatus(),
                outbox.getAttempts(),
                outbox.getMaxAttempts(),
                outbox.getNextAttemptAt(),
                outbox.getLastError(),
                outbox.getCreatedAt(),
                outbox.getUpdatedAt()
        );
    }
}
