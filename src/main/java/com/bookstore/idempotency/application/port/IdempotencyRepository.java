package com.bookstore.idempotency.application.port;

import com.bookstore.idempotency.domain.IdempotencyRequest;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface IdempotencyRepository {
    Optional<IdempotencyRequest> findByKey(
            Long userId,
            String operation,
            String idempotencyKey);

    // Atomically insert a new record. Returns empty if another request has already claimed the same key.
    Optional<IdempotencyRequest> tryClaim(
            Long userId,
            String operation,
            String idempotencyKey,
            String requestHash,
            OffsetDateTime expiresAt);

    // Store the HTTP response once the business operation succeeds
    void complete(
            Long id,
            int responseStatus,
            String responseBody);

    // After an idempotency record expires and is deleted, its key can be reused.
    // This means the 24-hour idempotency guarantee applies only within that retention window
    int deleteExpired(int batchSize);
}
