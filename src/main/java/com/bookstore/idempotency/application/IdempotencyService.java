package com.bookstore.idempotency.application;

import com.bookstore.idempotency.application.exception.IdempotencyKeyConflictException;
import com.bookstore.idempotency.application.exception.IdempotencyRequestInProgressException;
import com.bookstore.idempotency.application.port.IdempotencyRepository;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import com.bookstore.idempotency.domain.IdempotencyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final int EXPIRATION_HOURS = 24;

    private final IdempotencyRepository repository;
    private final JsonMapper jsonMapper;

    public IdempotencyService(IdempotencyRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public ResponseEntity<?> execute(
            Long userId,
            IdempotencyOperation operation,
            String idempotencyKey,
            String requestHash,
            Supplier<ResponseEntity<?>> action) {

        validate(userId, operation, idempotencyKey, requestHash, action);

        OffsetDateTime expiresAt = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plusHours(EXPIRATION_HOURS);

        // Attempt to claim the idempotency key.
        Optional<IdempotencyRequest> claimed = repository.tryClaim(
                userId,
                operation,
                idempotencyKey,
                requestHash,
                expiresAt);

        if (claimed.isPresent()) {
            // New request: execute and store its result.
            IdempotencyRequest request = claimed.get();

            // get the result from the executed business logic passed as lambda
            // if the business logic fails, the idempotency record is rollback as well because they are in the same @Transaction
            // (the business logic's @Transaction extends this one)
            ResponseEntity<?> response = action.get();

            String responseBody = response.getBody() == null
                    ? null
                    : jsonMapper.writeValueAsString(response.getBody());

            repository.complete(request.getId(), response.getStatusCode().value(), responseBody);

            return response;
        }

        // Existing key: retrieve the original request.
        IdempotencyRequest existing = repository.findByKey(userId, operation, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record could not be found"));

        // Same key must always represent the same payload. Otherwise, the new request uses already used I.K.
        if (!existing.getRequestHash().equals(requestHash)) {
            log.debug("Idempotency request conflict operation={} recordId={}", operation, existing.getId());
            throw new IdempotencyKeyConflictException("Idempotency key was already used with a different request");
        }

        // An incomplete record cannot be replayed.
        // Although currently all requests are completed after the execution of the action.
        if (!existing.isCompleted()) {
            log.debug("Idempotency request in progress operation={} recordId={}", operation, existing.getId());
            throw new IdempotencyRequestInProgressException("Request is still being processed");
        }

        // Return the previously stored HTTP response.
        ResponseEntity<?> response = replay(existing);
        log.debug("Replayed idempotent response operation={} recordId={} status={}",
                operation, existing.getId(), response.getStatusCode().value());
        return response;
    }

    // An expired key can still replay its old response until the cleanup actually deletes it.
    private ResponseEntity<?> replay(IdempotencyRequest request) {
        int status = request.getResponseStatus();
        String body = request.getResponseBody();

        if (body == null) {
            return ResponseEntity.status(status).build();
        }

        // converts the saved JSON into a Jackson JSON tree rather than reconstructing the Object (BookResponse for exmaple)
        return ResponseEntity.status(status)
                .body(jsonMapper.readTree(body));
    }

    private void validate(Long userId, IdempotencyOperation operation, String idempotencyKey, String requestHash, Supplier<ResponseEntity<?>> action) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Valid user ID is required");
        }

        Objects.requireNonNull(operation, "Operation is required");
        Objects.requireNonNull(action, "Action is required");

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128
                || !idempotencyKey.equals(idempotencyKey.trim())
                || idempotencyKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }

        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid request hash");
        }
    }
}
