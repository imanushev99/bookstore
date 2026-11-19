package com.bookstore.order.application;

import com.bookstore.common.application.AfterCommit;
import com.bookstore.inventory.application.InventoryAllocationService;
import com.bookstore.messaging.application.port.ProcessedEventRepository;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);
    private static final String CONSUMER_NAME = "ORDER_PROCESSOR";

    private final OrderRepository orderRepository;
    private final InventoryAllocationService inventoryAllocationService;
    private final ProcessedEventRepository processedEventRepository;

    public OrderProcessingService(
            OrderRepository orderRepository,
            InventoryAllocationService inventoryAllocationService,
            ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.inventoryAllocationService = inventoryAllocationService;
        this.processedEventRepository = processedEventRepository;
    }

    // BEGIN
    //  1. Mark Kafka event as processed
    //  2. Find order and its items
    //  3. Lock book rows and allocate inventory
    //  4. Update affected order items and statuses
    // COMMIT
    @Transactional
    public void processOrder(UUID eventId, Long orderId) {
        // Prevent processing the same Kafka event twice (deduplication)
        if (!processedEventRepository.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            log.debug("Skipped duplicate order event orderId={} eventId={}", orderId, eventId);
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        // Protect against different events processing the same order after it has already been classified.
        if (order.getStatus() != OrderStatus.PENDING) {
            log.debug("Skipped order processing orderId={} eventId={} status={}",
                    orderId, eventId, order.getStatus());
            return;
        }

        List<OrderItem> items = orderRepository.findItemsByOrderId(orderId);

        if (items.isEmpty()) {
            throw new IllegalStateException("Order has no items: " + orderId);
        }

        // Always lock books in a consistent order.
        List<Long> bookIds = items.stream()
                .map(OrderItem::getBookId)
                .distinct()
                .sorted()
                .toList();

        Set<Long> affectedOrderIds = new HashSet<>();
        affectedOrderIds.add(orderId);

        for (Long bookId : bookIds) {
            inventoryAllocationService.allocateOutstandingForBook(bookId);
        }

        AfterCommit.run(() -> log.info("Order processed orderId={} eventId={}", orderId, eventId));
    }
}
