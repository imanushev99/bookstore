package com.bookstore.order.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.messaging.application.port.ProcessedEventRepository;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderProcessingService {

    private static final String CONSUMER_NAME = "ORDER_PROCESSOR";

    private final OrderRepository orderRepository;
    private final BookRepository bookRepository;
    private final ProcessedEventRepository processedEventRepository;

    public OrderProcessingService(
            OrderRepository orderRepository,
            BookRepository bookRepository,
            ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void processOrder(UUID eventId, Long orderId) {
        // Prevent processing the same Kafka event twice (deduplication)
        if (!processedEventRepository.tryMarkProcessed(eventId, CONSUMER_NAME)) {
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        // Protect against different events processing the same order after it has already been classified.
        if (order.getStatus() != OrderStatus.PENDING) {
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

            // Exclusive lock, including archived books that
            // may still belong to previously submitted orders.
            // Archived books are removed only from carts, not from orders.
            Book book = bookRepository
                    .findByIdForInventoryUpdate(bookId)
                    .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));

            int availableStock = book.getStock();

            // No stock: classify our order item as backordered.
            // No need to scan every older backorder.
            if (availableStock == 0) {
                OrderItem item = items.stream()
                        .filter(i -> i.getBookId().equals(bookId))
                        .findFirst()
                        .orElseThrow();

                int remaining = item.getQuantity() - item.getAllocatedQuantity();

                if (item.getBackorderedQuantity() != remaining) {
                    orderRepository.updateItemAllocation(item.getId(), item.getAllocatedQuantity(), remaining);
                }

                continue;
            }

            // Must include older PENDING orders as well as previously BACKORDERED orders.
            List<OrderItem> outstanding = orderRepository.findOutstandingItemsByBookId(bookId);

            for (OrderItem item : outstanding) {

                int remaining = item.getQuantity() - item.getAllocatedQuantity();

                int allocatedNow = Math.min(availableStock, remaining);

                int newAllocated = item.getAllocatedQuantity() + allocatedNow;

                int newBackordered = item.getQuantity() - newAllocated;

                if (allocatedNow > 0 || newBackordered != item.getBackorderedQuantity()) {

                    orderRepository.updateItemAllocation(item.getId(), newAllocated, newBackordered);

                    affectedOrderIds.add(item.getOrderId());
                }

                availableStock -= allocatedNow;

                if (availableStock == 0) {
                    break;
                }
            }

            int allocatedStock = book.getStock() - availableStock;

            if (allocatedStock > 0) {
                bookRepository.decreaseStock(bookId, allocatedStock);
            }

            // Ensure this order's item is classified even if
            // older demand consumed all available stock.
            OrderItem currentItem = orderRepository
                    .findItemsByOrderId(orderId)
                    .stream()
                    .filter(i -> i.getBookId().equals(bookId))
                    .findFirst()
                    .orElseThrow();

            int remaining = currentItem.getQuantity() - currentItem.getAllocatedQuantity();

            if (currentItem.getBackorderedQuantity() != remaining) {
                orderRepository.updateItemAllocation(currentItem.getId(), currentItem.getAllocatedQuantity(), remaining);
            }
        }

        // Recalculate statuses for all affected orders.
        affectedOrderIds.stream()
                .sorted()
                .forEach(orderRepository::refreshAllocationStatus);
    }
}
