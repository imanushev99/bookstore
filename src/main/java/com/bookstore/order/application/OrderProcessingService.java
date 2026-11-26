package com.bookstore.order.application;

import com.bookstore.book.application.port.BookRepository;
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
    private final BookRepository bookRepository;
    private final InventoryAllocationService inventoryAllocationService;
    private final ProcessedEventRepository processedEventRepository;

    public OrderProcessingService(
            OrderRepository orderRepository,
            BookRepository bookRepository,
            InventoryAllocationService inventoryAllocationService,
            ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.inventoryAllocationService = inventoryAllocationService;
        this.processedEventRepository = processedEventRepository;
    }

    // BEGIN
    //    Deduplicate Kafka event
    //    Load and validate order
    //    Find distinct book IDs
    //
    //    Lock all book rows in ID order
    //    For each book:
    //        Find outstanding orders (oldest first)
    //        Allocate available inventory
    //        Update order items
    //        Recalculate order statuses
    // COMMIT
    // Acknowledge Kafka message
    @Transactional
    public void processOrder(UUID eventId, Long orderId) {
        // Prevent processing the same Kafka event twice (deduplication)
        if (!processedEventRepository.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            log.debug("Skipped duplicate order event orderId={} eventId={}", orderId, eventId);
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        // Another Kafka event or inventory restock operation might have already
        // allocated inventory to this order and changed its status.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PROCESSING) {
            log.debug("Skipped order processing orderId={} eventId={} status={}", orderId, eventId, order.getStatus());
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

        // Allocation can lock other orders while refreshing their statuses.
        // Lock every book first so cancellation cannot hold a later book
        // while waiting for an order row this transaction already holds.
        for (Long bookId : bookIds) {
            bookRepository.findByIdForInventoryUpdate(bookId)
                    .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));
        }

        for (Long bookId : bookIds) {
            inventoryAllocationService.allocateOutstandingForBook(bookId);
        }

        AfterCommit.run(() -> log.info("Order processed orderId={} eventId={}", orderId, eventId));
    }
}
