
package com.bookstore.outbox.application.port;

import com.bookstore.outbox.domain.OutboxEvent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    OutboxEvent save(OutboxEvent event);

    // Atomically claim a batch of events ready for publishing
    List<OutboxEvent> claimPending(int batchSize, UUID claimToken, OffsetDateTime lockedUntil);

    // Mark an event as published after Kafka acknowledges it
    boolean markPublished(UUID eventId, UUID claimToken);

    // Schedule another attempt after a publishing failure
    boolean releaseForRetry(
            UUID eventId,
            UUID claimToken,
            OffsetDateTime nextAttemptAt,
            String lastError);

    // Recover events claimed by workers that crashed
    int requeueExpiredClaims(int batchSize);
}
