package com.orderflow.service;

import com.orderflow.dto.request.WebhookSubscriptionRequest;
import com.orderflow.dto.response.WebhookDeliveryResponse;
import com.orderflow.dto.response.WebhookSubscriptionResponse;
import com.orderflow.entity.OutboxStatus;
import com.orderflow.entity.WebhookOutbox;
import com.orderflow.entity.WebhookSubscription;
import com.orderflow.exception.BadRequestException;
import com.orderflow.exception.ResourceNotFoundException;
import com.orderflow.repository.WebhookOutboxRepository;
import com.orderflow.repository.WebhookSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WebhookService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookOutboxRepository outboxRepository;

    public WebhookService(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookOutboxRepository outboxRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public WebhookSubscriptionResponse createSubscription(WebhookSubscriptionRequest request) {
        WebhookSubscription subscription = new WebhookSubscription(
                request.url(),
                request.secret(),
                request.eventTypes()
        );
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        return WebhookSubscriptionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> getSubscriptions() {
        return subscriptionRepository.findAll().stream()
                .map(WebhookSubscriptionResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteSubscription(Long id) {
        WebhookSubscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook subscription not found with id: " + id));
        subscriptionRepository.delete(sub);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> getDeliveries() {
        return outboxRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(WebhookDeliveryResponse::fromEntity)
                .toList();
    }

    @Transactional
    public WebhookDeliveryResponse retryDelivery(Long id) {
        WebhookOutbox outbox = outboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outbox delivery not found with id: " + id));

        if (outbox.getStatus() == OutboxStatus.DELIVERED) {
            throw new BadRequestException("Delivery is already in DELIVERED status");
        }

        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(Instant.now());
        outbox.setLastError(null);

        WebhookOutbox updated = outboxRepository.save(outbox);
        return WebhookDeliveryResponse.fromEntity(updated);
    }
}
