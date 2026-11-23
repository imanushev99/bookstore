package com.bookstore.outbox.application;

import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.outbox.application.port.OutboxRepository;
import com.bookstore.outbox.domain.OutboxEvent;
import com.bookstore.outbox.domain.OutboxEventType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final OutboxRepository outboxRepository;
    private final OrderRepository orderRepository;

    public OutboxClaimService(OutboxRepository outboxRepository, OrderRepository orderRepository) {
        this.outboxRepository = outboxRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public List<OutboxEvent> claimAndMark(int batchSize, UUID claimToken, OffsetDateTime lockedUntil) {

        // 1. Claim pending outbox events.
        List<OutboxEvent> events = outboxRepository.claimPending(batchSize, claimToken, lockedUntil);

        // 2. Mark related orders as PROCESSING.
        for (OutboxEvent event : events) {
            if (event.getEventType() == OutboxEventType.ORDER_SUBMITTED) {
                orderRepository.markProcessingIfPending(event.getAggregateId());
            }
        }

        return events;
    }
}