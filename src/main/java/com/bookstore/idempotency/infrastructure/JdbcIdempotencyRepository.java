package com.bookstore.idempotency.infrastructure;

import com.bookstore.idempotency.application.port.IdempotencyRepository;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import com.bookstore.idempotency.domain.IdempotencyRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRequest> findByKey(Long userId, IdempotencyOperation operation, String idempotencyKey) {
        String sql = """
                SELECT *
                FROM idempotency_requests
                WHERE user_id = :userId
                  AND operation = :operation
                  AND idempotency_key = :idempotencyKey
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("operation", operation.name())
                .addValue("idempotencyKey", idempotencyKey);

        List<IdempotencyRequest> requests = jdbc.query(sql, params, this::mapRequest);

        return requests.stream().findFirst();
    }

    @Override
    public Optional<IdempotencyRequest> tryClaim(Long userId, IdempotencyOperation operation, String idempotencyKey, String requestHash, OffsetDateTime expiresAt) {
        // DO NOTHING is the key for the UNIQUE CONSTRAINT
        // If the first transaction is still uncommitted, the competing insert can wait for its outcome.
        // - If Request A rolls back, Request B can instead acquire the claim and execute the operation.
        // - If Request A commits, Request B's tryClaim() returns empty and the service will return the stored response.
        String sql = """
                INSERT INTO idempotency_requests (
                    user_id,
                    operation,
                    idempotency_key,
                    request_hash,
                    expires_at)
                VALUES (
                    :userId,
                    :operation,
                    :idempotencyKey,
                    :requestHash,
                    :expiresAt)
                ON CONFLICT (
                    user_id,
                    operation,
                    idempotency_key) DO NOTHING
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("operation", operation.name())
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("requestHash", requestHash)
                .addValue("expiresAt", expiresAt);

        List<IdempotencyRequest> requests = jdbc.query(sql, params, this::mapRequest);

        return requests.stream().findFirst();
    }

    @Override
    public void complete(Long id, int responseStatus, String responseBody) {
        String sql = """
            UPDATE idempotency_requests
            SET response_status = :responseStatus,
                response_body = CAST(:responseBody AS jsonb)
            WHERE id = :id
              AND response_status IS NULL
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("responseStatus", responseStatus)
                .addValue("responseBody", responseBody);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Idempotency request not found or already completed");
        }
    }


    // Without SKIP LOCKED, if two instances of the Bookstore app execute cleanup simultaneously,
    // it might attempt to lock the same expired records, causing one cleanup transaction to wait.
    // Also, an expired key can still replay its old response until the cleanup actually deletes it.
    @Override
    public int deleteExpired(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        String sql = """
            WITH expired AS (
                SELECT id
                FROM idempotency_requests
                WHERE expires_at <= CURRENT_TIMESTAMP
                ORDER BY expires_at, id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            DELETE FROM idempotency_requests ir
            USING expired
            WHERE ir.id = expired.id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchSize", batchSize);

        return jdbc.update(sql, params);
    }


    private IdempotencyRequest mapRequest(ResultSet rs, int rowNum) throws SQLException {
        Integer responseStatus =
                rs.getObject("response_status") == null
                        ? null
                        : rs.getInt("response_status");

        return new IdempotencyRequest(
                rs.getLong("id"),
                rs.getLong("user_id"),
                IdempotencyOperation.valueOf(rs.getString("operation")),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                responseStatus,
                rs.getString("response_body"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class)
        );
    }

}
