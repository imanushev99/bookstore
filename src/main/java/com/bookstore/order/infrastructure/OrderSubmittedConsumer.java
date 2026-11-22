
package com.bookstore.order.infrastructure;

import com.bookstore.order.application.OrderProcessingService;
import com.bookstore.outbox.domain.OutboxEventType;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
public class OrderSubmittedConsumer {

    private final OrderProcessingService orderProcessingService;
    private final JsonMapper jsonMapper;

    public OrderSubmittedConsumer(OrderProcessingService orderProcessingService, JsonMapper jsonMapper) {
        this.orderProcessingService = orderProcessingService;
        this.jsonMapper = jsonMapper;
    }

    // ackMode = "MANUAL_IMMEDIATE": our application manually acknowledges successful processing rather than
    //                               relying on an automatic acknowledgment after the listener returns.
    @KafkaListener(
            topics = "${bookstore.kafka.topics.order-submitted}",
            groupId = "bookstore-order-processor",
            ackMode = "MANUAL_IMMEDIATE")
    public void consume(
            @Payload String payload,
            @Header("eventId") String eventIdHeader,
            @Header("eventType") String eventTypeHeader,
            Acknowledgment acknowledgment) {

        if (!OutboxEventType.ORDER_SUBMITTED.name().equals(eventTypeHeader)) {
            throw new IllegalArgumentException("Unexpected event type: " + eventTypeHeader);
        }

        UUID eventId = UUID.fromString(eventIdHeader);

        JsonNode event = jsonMapper.readTree(payload);
        JsonNode orderIdNode = event.get("orderId");

        if (orderIdNode == null || !orderIdNode.canConvertToLong() || orderIdNode.longValue() <= 0) {
            throw new IllegalArgumentException("Invalid OrderSubmitted event payload");
        }

        Long orderId = orderIdNode.longValue();

        orderProcessingService.processOrder(eventId, orderId);

        // Acknowledge only after successful processing.
        acknowledgment.acknowledge();
    }
}
