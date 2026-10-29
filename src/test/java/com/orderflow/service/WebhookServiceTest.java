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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookSubscriptionRepository subscriptionRepository;

    @Mock
    private WebhookOutboxRepository outboxRepository;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(subscriptionRepository, outboxRepository);
    }

    @Test
    void createSubscription_Success() {
        WebhookSubscriptionRequest req = new WebhookSubscriptionRequest("https://example.com/hook", "secret12345", "order.created");
        WebhookSubscription sub = new WebhookSubscription("https://example.com/hook", "secret12345", "order.created");
        sub.setId(1L);

        when(subscriptionRepository.save(any(WebhookSubscription.class))).thenReturn(sub);

        WebhookSubscriptionResponse response = webhookService.createSubscription(req);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.url()).isEqualTo("https://example.com/hook");
    }

    @Test
    void getSubscriptions_ReturnsList() {
        WebhookSubscription sub = new WebhookSubscription("https://example.com/hook", "secret12345", "order.created");
        sub.setId(1L);
        when(subscriptionRepository.findAll()).thenReturn(List.of(sub));

        List<WebhookSubscriptionResponse> list = webhookService.getSubscriptions();

        assertThat(list).hasSize(1);
    }

    @Test
    void deleteSubscription_Success() {
        WebhookSubscription sub = new WebhookSubscription("https://example.com/hook", "secret12345", "order.created");
        sub.setId(1L);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        webhookService.deleteSubscription(1L);

        verify(subscriptionRepository).delete(sub);
    }

    @Test
    void deleteSubscription_NotFound_ThrowsException() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.deleteSubscription(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDeliveries_ReturnsList() {
        WebhookOutbox outbox = new WebhookOutbox("order.created", "ORD-1", "{}");
        outbox.setId(10L);
        when(outboxRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(outbox));

        List<WebhookDeliveryResponse> deliveries = webhookService.getDeliveries();

        assertThat(deliveries).hasSize(1);
    }

    @Test
    void retryDelivery_AlreadyDelivered_ThrowsBadRequest() {
        WebhookOutbox outbox = new WebhookOutbox("order.created", "ORD-1", "{}");
        outbox.setId(10L);
        outbox.setStatus(OutboxStatus.DELIVERED);

        when(outboxRepository.findById(10L)).thenReturn(Optional.of(outbox));

        assertThatThrownBy(() -> webhookService.retryDelivery(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in DELIVERED status");
    }

    @Test
    void retryDelivery_FailedDelivery_ResetsStatus() {
        WebhookOutbox outbox = new WebhookOutbox("order.created", "ORD-1", "{}");
        outbox.setId(10L);
        outbox.setStatus(OutboxStatus.FAILED);
        outbox.setAttempts(3);

        when(outboxRepository.findById(10L)).thenReturn(Optional.of(outbox));
        when(outboxRepository.save(any(WebhookOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookDeliveryResponse response = webhookService.retryDelivery(10L);

        assertThat(response.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(response.attempts()).isEqualTo(0);
    }
}
