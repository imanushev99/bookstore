
package com.bookstore.inventory.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.common.application.AfterCommit;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.OrderItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class InventoryAllocationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAllocationService.class);

    private final BookRepository bookRepository;
    private final OrderRepository orderRepository;

    public InventoryAllocationService(BookRepository bookRepository, OrderRepository orderRepository) {
        this.bookRepository = bookRepository;
        this.orderRepository = orderRepository;
    }

    // Propagation.MANDATORY means that it requires the caller method to have an active transaction
    // (for example when called from processOrder, or restockBook)
    @Transactional(propagation = Propagation.MANDATORY)
    public void allocateOutstandingForBook(Long bookId) {

        // Serialize inventory allocation for this book.
        Book book = bookRepository
                .findByIdForInventoryUpdate(bookId)
                .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));

        int availableStock = book.getStock();

        // Includes PENDING and BACKORDERED orders, sorted by creation time (oldest first).
        List<OrderItem> outstandingItems = orderRepository.findOutstandingItemsByBookId(bookId);

        Set<Long> affectedOrderIds = new HashSet<>();

        for (OrderItem item : outstandingItems) {

            int remaining = item.getQuantity() - item.getAllocatedQuantity();

            int allocatedNow = Math.min(availableStock, remaining);

            int newAllocated = item.getAllocatedQuantity() + allocatedNow;

            int newBackordered = item.getQuantity() - newAllocated;

            boolean allocationChanged =
                    newAllocated != item.getAllocatedQuantity()
                            || newBackordered != item.getBackorderedQuantity();

            if (allocationChanged) {
                orderRepository.updateItemAllocation(item.getId(), newAllocated, newBackordered);

                affectedOrderIds.add(item.getOrderId());
            }

            availableStock -= allocatedNow;
        }

        int allocatedStock = book.getStock() - availableStock;

        if (allocatedStock > 0) {
            bookRepository.decreaseStock(bookId, allocatedStock);
        }

        // Recalculate statuses after updating allocations.
        affectedOrderIds.stream()
                .sorted()
                .forEach(orderRepository::refreshAllocationStatus);

        if (!affectedOrderIds.isEmpty() && log.isDebugEnabled()) {
            AfterCommit.run(() -> log.debug("Inventory allocation updated bookId={} quantityAllocated={} affectedOrderCount={}",
                    bookId, allocatedStock, affectedOrderIds.size()));
        }
    }
}
