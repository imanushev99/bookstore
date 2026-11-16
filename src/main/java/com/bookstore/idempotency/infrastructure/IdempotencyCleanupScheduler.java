package com.bookstore.idempotency.infrastructure;

import com.bookstore.idempotency.application.port.IdempotencyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;


@Configuration
@EnableScheduling
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final IdempotencyRepository repository;

    public IdempotencyCleanupScheduler(IdempotencyRepository repository) {
        this.repository = repository;
    }

    /**
     * initialDelay = 60000 starts the first cleanup approximately one minute after application startup
     * fixedDelayString = a default interval of 15 minutes
     * Each SQL execution deletes at most 500 records
     * The loop processes at most 20 batches, meaning a maximum of 10,000 records per scheduled execution.
     *
     * Important: No @Transactional here. Each repository deletion executes as its own atomic SQL statement, keeping database locks short-lived
     */
    @Scheduled(fixedDelayString = "${bookstore.idempotency.cleanup-delay-ms:900000}", initialDelay = 60000)
    public void cleanupExpiredRequests() {
        int totalDeleted = 0;

        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = repository.deleteExpired(BATCH_SIZE);

            totalDeleted += deleted;

            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("Deleted {} expired idempotency records", totalDeleted);
        }
    }
}
