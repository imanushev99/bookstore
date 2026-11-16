package com.bookstore.messaging.application.port;

import java.util.UUID;

public interface ProcessedEventRepository {

    // returns true when an event is being processed for the first time
    // and false when it has already been processed by that logical consumer
    boolean tryMarkProcessed(UUID eventId, String consumerName);
}
