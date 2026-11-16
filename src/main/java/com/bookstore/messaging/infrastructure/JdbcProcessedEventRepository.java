
package com.bookstore.messaging.infrastructure;

import com.bookstore.messaging.application.port.ProcessedEventRepository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcProcessedEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Deduplication: Guarantees consumer is idempotent.
    // provides effectively-once database effects for duplicate deliveries of the same event
    @Override
    public boolean tryMarkProcessed(UUID eventId, String consumerName) {
        String sql = """
                INSERT INTO processed_kafka_events (
                    event_id,
                    consumer_name)
                VALUES (
                    :eventId,
                    :consumerName)
                ON CONFLICT (event_id, consumer_name)
                DO NOTHING
                RETURNING event_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("consumerName", consumerName);

        return !jdbc.query(
                sql,
                params,
                (rs, rowNum) -> rs.getObject("event_id", UUID.class))
                .isEmpty();
    }
}
