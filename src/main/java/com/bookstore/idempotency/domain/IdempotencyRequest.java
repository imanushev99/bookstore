package com.bookstore.idempotency.domain;

import java.time.OffsetDateTime;

/**
 * idempotencyKey = random UUID generated on the customer's side.
 *
 * requestHash = Ensures that the same idempotency key is not reused for a different operation payload.
 *             => Generate a new idempotency key for every new logical operation, even when the payload is different.
 *             If there are 2 requests with the same idempotency key, userId, and operation
 *             but with different request body represented by requestHash (for example 2 different books for creation)
 *             then the 2nd request will still fail, regardless it is for a different book, because of the unique constraint
 *             (idempotency key, userId, and operation).
 *
 * expiresAt = Defines when the record becomes eligible for cleanup.
 */
public class IdempotencyRequest {
    private final Long id;
    private final Long userId;
    private final String operation;
    private final String idempotencyKey;
    private final String requestHash;
    private final Integer responseStatus;
    private final String responseBody;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime expiresAt;

    public IdempotencyRequest(
            Long id,
            Long userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Integer responseStatus,
            String responseBody,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isCompleted() {
        return responseStatus != null;
    }
}
