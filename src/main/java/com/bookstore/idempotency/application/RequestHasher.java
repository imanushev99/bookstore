package com.bookstore.idempotency.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
public class RequestHasher {
    private final JsonMapper jsonMapper;

    public RequestHasher(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper.rebuild()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    // Including the request target is useful because the same payload sent to different resource URLs should represent different requests
    // When another request arrives with the same (user_id, operation, idempotency_key) we will compare the hashes:
    // - Same hash      = Return the original stored response (the request was repeated)
    // - Different hash = Return 409 Conflict (the request was sent with the same idempotency key as some previous request)
    public String hash(String requestTarget, Object requestBody) {
        if (requestTarget == null || requestTarget.isBlank()) {
            throw new IllegalArgumentException("Request target is required");
        }

        Objects.requireNonNull(requestBody, "Request body is required");

        // Serialize the operation target and request payload.
        byte[] requestBytes = jsonMapper.writeValueAsBytes(List.of(requestTarget, requestBody));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(requestBytes);

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
