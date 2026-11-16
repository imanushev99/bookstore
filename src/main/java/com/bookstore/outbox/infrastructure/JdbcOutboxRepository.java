
package com.bookstore.outbox.infrastructure;

import com.bookstore.outbox.application.port.OutboxRepository;
import com.bookstore.outbox.domain.OutboxEvent;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        String sql = """
                INSERT INTO outbox_events (
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    payload)
                VALUES (
                    :aggregateType,
                    :aggregateId,
                    :eventType,
                    CAST(:payload AS jsonb) )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("aggregateType", event.getAggregateType())
                .addValue("aggregateId", event.getAggregateId())
                .addValue("eventType", event.getEventType())
                .addValue("payload", event.getPayload());

        return jdbc.queryForObject(sql, params, this::mapEvent);
    }

    // Atomically claims available events using SKIP LOCKED
    @Override
    public List<OutboxEvent> claimPending(int batchSize, UUID claimToken, OffsetDateTime lockedUntil) {
        String sql = """
                WITH candidates AS (
                    SELECT id
                    FROM outbox_events
                    WHERE status = 'PENDING'
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY next_attempt_at, created_at, id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events e
                SET status = 'IN_PROGRESS',
                    attempts = e.attempts + 1,
                    claim_token = :claimToken,
                    locked_until = :lockedUntil
                FROM candidates c
                WHERE e.id = c.id
                RETURNING e.*
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("claimToken", claimToken)
                .addValue("lockedUntil", lockedUntil);

        return jdbc.query(sql, params, this::mapEvent);
    }

    @Override
    public boolean markPublished(UUID eventId, UUID claimToken) {
        String sql = """
                UPDATE outbox_events
                SET status = 'PUBLISHED',
                    published_at = CURRENT_TIMESTAMP,
                    claim_token = NULL,
                    locked_until = NULL,
                    last_error = NULL
                WHERE id = :eventId
                  AND status = 'IN_PROGRESS'
                  AND claim_token = :claimToken
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("claimToken", claimToken);

        return jdbc.update(sql, params) == 1;
    }

    // Releases a failed event and schedules its next attempt
    @Override
    public boolean releaseForRetry(
            UUID eventId,
            UUID claimToken,
            OffsetDateTime nextAttemptAt,
            String lastError) {
        String sql = """
                UPDATE outbox_events
                SET status = 'PENDING',
                    next_attempt_at = :nextAttemptAt,
                    claim_token = NULL,
                    locked_until = NULL,
                    last_error = :lastError
                WHERE id = :eventId
                  AND status = 'IN_PROGRESS'
                  AND claim_token = :claimToken
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("claimToken", claimToken)
                .addValue("nextAttemptAt", nextAttemptAt)
                .addValue("lastError", lastError);

        return jdbc.update(sql, params) == 1;
    }

    // Makes abandoned events available for publishing again
    @Override
    public int requeueExpiredClaims(int batchSize) {
        String sql = """
                WITH expired AS (
                    SELECT id
                    FROM outbox_events
                    WHERE status = 'IN_PROGRESS'
                      AND locked_until <= CURRENT_TIMESTAMP
                    ORDER BY locked_until, id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED)
                UPDATE outbox_events e
                SET status = 'PENDING',
                    next_attempt_at = CURRENT_TIMESTAMP,
                    claim_token = NULL,
                    locked_until = NULL,
                    last_error = 'Publisher claim expired'
                FROM expired x
                WHERE e.id = x.id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchSize", batchSize);

        return jdbc.update(sql, params);
    }

    private OutboxEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getString("aggregate_type"),
                rs.getLong("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                OutboxEvent.Status.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getObject("next_attempt_at", OffsetDateTime.class),
                rs.getObject("claim_token", UUID.class),
                rs.getObject("locked_until", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("published_at", OffsetDateTime.class),
                rs.getString("last_error")
        );
    }
}
