
package com.bookstore.outbox.infrastructure;

import com.bookstore.outbox.domain.OutboxEvent;
import com.bookstore.outbox.domain.OutboxEventType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Component
public class KafkaOutboxProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String orderSubmittedTopic;
    private String inventoryRestockedTopic;

    public KafkaOutboxProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${bookstore.kafka.topics.order-submitted}") String orderSubmittedTopic,
            @Value("${bookstore.kafka.topics.inventory-restocked}") String inventoryRestockedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderSubmittedTopic = orderSubmittedTopic;
        this.inventoryRestockedTopic = inventoryRestockedTopic;
    }

    public CompletableFuture<SendResult<String, String>> publish(OutboxEvent event) {

        String topic = resolveTopic(event.getEventType());

        String key = event.getAggregateType() + ":" + event.getAggregateId();

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, event.getPayload());

        record.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));

        record.headers().add("eventType", event.getEventType().name().getBytes(StandardCharsets.UTF_8));

        // returns a CompletableFuture, allowing us to determine when Kafka acknowledges the message
        return kafkaTemplate.send(record);
    }

    private String resolveTopic(OutboxEventType eventType) {
        return switch (eventType) {
            case ORDER_SUBMITTED -> orderSubmittedTopic;
            case BOOK_RESTOCKED -> inventoryRestockedTopic;
        };
    }
}
