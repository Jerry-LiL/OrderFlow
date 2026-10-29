package com.orderflow.service;

import com.orderflow.entity.OutboxStatus;
import com.orderflow.entity.WebhookOutbox;
import com.orderflow.entity.WebhookSubscription;
import com.orderflow.repository.WebhookOutboxRepository;
import com.orderflow.repository.WebhookSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookOutboxRepository outboxRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final RestClient restClient;

    public WebhookDispatcher(
            WebhookOutboxRepository outboxRepository,
            WebhookSubscriptionRepository subscriptionRepository
    ) {
        this.outboxRepository = outboxRepository;
        this.subscriptionRepository = subscriptionRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void scheduleDispatch() {
        dispatchPendingEvents();
    }

    @Transactional
    public int dispatchPendingEvents() {
        Instant now = Instant.now();
        List<WebhookOutbox> pending = outboxRepository.findPendingDeliveries(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                now,
                PageRequest.of(0, 20)
        );

        if (pending.isEmpty()) {
            return 0;
        }

        List<WebhookSubscription> activeSubscriptions = subscriptionRepository.findByActiveTrue();
        int processedCount = 0;

        for (WebhookOutbox outbox : pending) {
            processedCount++;
            processOutboxItem(outbox, activeSubscriptions);
        }

        return processedCount;
    }

    private void processOutboxItem(WebhookOutbox outbox, List<WebhookSubscription> activeSubscriptions) {
        List<WebhookSubscription> matchingSubs = activeSubscriptions.stream()
                .filter(sub -> isSubscribedToEvent(sub.getEventTypes(), outbox.getEventType()))
                .toList();

        if (matchingSubs.isEmpty()) {
            // No subscribers for this event; mark delivered to prevent endless retries
            log.debug("No active subscribers for event {}, marking DELIVERED", outbox.getEventType());
            outbox.setStatus(OutboxStatus.DELIVERED);
            outboxRepository.save(outbox);
            return;
        }

        boolean allSucceeded = true;
        StringBuilder errorDetails = new StringBuilder();

        for (WebhookSubscription sub : matchingSubs) {
            boolean success = deliverToSubscriber(sub, outbox);
            if (!success) {
                allSucceeded = false;
                if (!errorDetails.isEmpty()) {
                    errorDetails.append("; ");
                }
                errorDetails.append("Failed delivering to: ").append(sub.getUrl());
            }
        }

        if (allSucceeded) {
            outbox.setStatus(OutboxStatus.DELIVERED);
            outbox.setLastError(null);
            log.info("Successfully delivered webhook event {} (id: {})", outbox.getEventType(), outbox.getId());
        } else {
            int newAttempts = outbox.getAttempts() + 1;
            outbox.setAttempts(newAttempts);
            outbox.setLastError(errorDetails.toString());

            if (newAttempts >= outbox.getMaxAttempts()) {
                outbox.setStatus(OutboxStatus.FAILED_PERMANENT);
                log.warn("Webhook event {} (id: {}) permanently failed after {} attempts",
                        outbox.getEventType(), outbox.getId(), newAttempts);
            } else {
                outbox.setStatus(OutboxStatus.FAILED);
                long backoffSeconds = (long) Math.min(300, Math.pow(2, newAttempts) * 2);
                outbox.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
                log.warn("Webhook delivery failed (attempt {}/{}); retrying in {}s for id: {}",
                        newAttempts, outbox.getMaxAttempts(), backoffSeconds, outbox.getId());
            }
        }

        outboxRepository.save(outbox);
    }

    private boolean deliverToSubscriber(WebhookSubscription sub, WebhookOutbox outbox) {
        try {
            String signature = calculateHmacSha256(sub.getSecret(), outbox.getPayload());

            restClient.post()
                    .uri(sub.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-OrderFlow-Event", outbox.getEventType())
                    .header("X-OrderFlow-Signature", "sha256=" + signature)
                    .header(HttpHeaders.USER_AGENT, "OrderFlow-Webhook-Dispatcher/1.0")
                    .body(outbox.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (Exception e) {
            log.error("Failed to post webhook to {}: {}", sub.getUrl(), e.getMessage());
            return false;
        }
    }

    private boolean isSubscribedToEvent(String registeredEvents, String eventType) {
        if (registeredEvents == null || registeredEvents.isBlank() || "*".equals(registeredEvents.trim())) {
            return true;
        }
        for (String ev : registeredEvents.split(",")) {
            if (ev.trim().equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }

    public static String calculateHmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating HMAC signature", e);
        }
    }
}
