
package com.bookstore.outbox.infrastructure;

import com.bookstore.outbox.application.port.OutboxRepository;
import com.bookstore.outbox.domain.OutboxEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 5;
    private static final int SEND_TIMEOUT_SECONDS = 10;
    private static final int CLAIM_DURATION_MINUTES = 2;

    private final OutboxRepository repository;
    private final KafkaOutboxProducer producer;

    public OutboxPublisher(OutboxRepository repository, KafkaOutboxProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${bookstore.outbox.poll-delay-ms:5000}", initialDelay = 10000)
    public void publishPendingEvents() {
        try {
            repository.requeueExpiredClaims(100);

            UUID claimToken = UUID.randomUUID();

            OffsetDateTime lockedUntil = OffsetDateTime
                    .now(ZoneOffset.UTC)
                    .plusMinutes(CLAIM_DURATION_MINUTES);

            List<OutboxEvent> events = repository.claimPending(BATCH_SIZE, claimToken, lockedUntil);

            for (OutboxEvent event : events) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                try {
                    publishEvent(event, claimToken);
                } catch (InterruptedException exception) {
                    // InterruptedException clears the interrupt flag that's why we need to set it manually after that
                    Thread.currentThread().interrupt();
                    scheduleRetry(event, claimToken, exception);
                    break;
                } catch (Exception exception) {
                    scheduleRetry(event, claimToken, exception);
                }
            }

        } catch (Exception exception) {
            log.error("Outbox publishing cycle failed", exception);
        }
    }

    private void publishEvent(OutboxEvent event, UUID claimToken) throws Exception {
        // Wait for Kafka acknowledgment.
        // Must handle checked exceptions from .get()
        producer.publish(event)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        boolean updated = repository.markPublished(event.getId(), claimToken);

        if (updated) {
            log.info("Published outbox event {}", event.getId());
        } else {
            log.warn("Could not mark event {} as published: claim changed", event.getId());
        }
    }

    private void scheduleRetry(OutboxEvent event, UUID claimToken, Exception exception) {
        int exponent = Math.min(event.getAttempts(), 8);
        long delaySeconds = (long) Math.pow(2, exponent);

        OffsetDateTime nextAttemptAt = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plusSeconds(delaySeconds);

        boolean released = repository.releaseForRetry(
                event.getId(),
                claimToken,
                nextAttemptAt,
                exception.toString());

        if (released) {
            log.warn("Publishing event {} failed. Retry in {} seconds", event.getId(), delaySeconds, exception);
        } else {
            log.warn("Could not release event {}: claim changed", event.getId());
        }
    }
}
