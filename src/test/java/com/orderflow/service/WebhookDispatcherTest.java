package com.orderflow.service;

import com.orderflow.entity.OutboxStatus;
import com.orderflow.entity.WebhookOutbox;
import com.orderflow.entity.WebhookSubscription;
import com.orderflow.repository.WebhookOutboxRepository;
import com.orderflow.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    private WebhookOutboxRepository outboxRepository;

    @Mock
    private WebhookSubscriptionRepository subscriptionRepository;

    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new WebhookDispatcher(outboxRepository, subscriptionRepository);
    }

    @Test
    void calculateHmacSha256_ProducesValidHex() {
        String secret = "my-secret-key-12345";
        String payload = "{\"orderId\": 100}";

        String signature1 = WebhookDispatcher.calculateHmacSha256(secret, payload);
        String signature2 = WebhookDispatcher.calculateHmacSha256(secret, payload);

        assertThat(signature1).isNotBlank();
        assertThat(signature1).isEqualTo(signature2);
        assertThat(signature1).matches("^[a-f0-9]{64}$");
    }

    @Test
    void dispatchPendingEvents_NoSubscribers_MarksDelivered() {
        WebhookOutbox outbox = new WebhookOutbox("order.created", "ORD-1", "{\"status\":\"CONFIRMED\"}");
        outbox.setId(1L);

        when(outboxRepository.findPendingDeliveries(any(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        int processed = dispatcher.dispatchPendingEvents();

        assertThat(processed).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        verify(outboxRepository).save(outbox);
    }

    @Test
    void dispatchPendingEvents_MaxAttemptsExceeded_MarksPermanentFailure() {
        WebhookOutbox outbox = new WebhookOutbox("order.created", "ORD-1", "{}");
        outbox.setId(1L);
        outbox.setAttempts(4);
        outbox.setMaxAttempts(5);

        WebhookSubscription sub = new WebhookSubscription("http://invalid-destination-domain-99999.test/webhook", "secret-test-key", "order.created");
        sub.setId(10L);

        when(outboxRepository.findPendingDeliveries(any(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        int processed = dispatcher.dispatchPendingEvents();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<WebhookOutbox> captor = ArgumentCaptor.forClass(WebhookOutbox.class);
        verify(outboxRepository).save(captor.capture());

        WebhookOutbox saved = captor.getValue();
        assertThat(saved.getAttempts()).isEqualTo(5);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.FAILED_PERMANENT);
    }
}
