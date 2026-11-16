
package com.bookstore.outbox.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OutboxEvent {

    public enum Status {
        PENDING,
        IN_PROGRESS,
        PUBLISHED
    }

    private final UUID id;
    private final String aggregateType;
    private final Long aggregateId;
    private final OutboxEventType eventType;
    private final String payload;
    private final Status status;
    private final int attempts;
    private final OffsetDateTime nextAttemptAt;
    // identifies which publisher currently owns a particular publishing attempt
    private final UUID claimToken;
    // if publisher crashes, another one will claim this event after the lockedUntil expires if the first publisher didn't start working again
    private final OffsetDateTime lockedUntil;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime publishedAt;
    private final String lastError;

    public OutboxEvent(
            UUID id,
            String aggregateType,
            Long aggregateId,
            OutboxEventType eventType,
            String payload,
            Status status,
            int attempts,
            OffsetDateTime nextAttemptAt,
            UUID claimToken,
            OffsetDateTime lockedUntil,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt,
            String lastError) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.claimToken = claimToken;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.lastError = lastError;
    }

    public static OutboxEvent pending(String aggregateType, Long aggregateId, OutboxEventType eventType, String payload) {
        return new OutboxEvent(
                null,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                Status.PENDING,
                0,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public OutboxEventType getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
