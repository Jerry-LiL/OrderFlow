package com.orderflow.repository;

import com.orderflow.entity.OutboxStatus;
import com.orderflow.entity.WebhookOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WebhookOutboxRepository extends JpaRepository<WebhookOutbox, Long> {

    @Query("SELECT o FROM WebhookOutbox o WHERE o.status IN :statuses AND o.nextAttemptAt <= :now AND o.attempts < o.maxAttempts ORDER BY o.createdAt ASC")
    List<WebhookOutbox> findPendingDeliveries(
            @Param("statuses") List<OutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    List<WebhookOutbox> findTop50ByOrderByCreatedAtDesc();
}
