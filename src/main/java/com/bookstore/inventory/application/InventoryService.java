
package com.bookstore.inventory.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class InventoryService {

    private final BookRepository bookRepository;
    private final InventoryAllocationService inventoryAllocationService;

    public InventoryService(BookRepository bookRepository, InventoryAllocationService inventoryAllocationService) {
        this.bookRepository = bookRepository;
        this.inventoryAllocationService = inventoryAllocationService;
    }

    // BEGIN
    //
    // 1. Lock book row
    // 2. Increase stock
    // 3. Find outstanding orders (oldest first)
    // 4. Allocate stock
    // 5. Update order items
    // 6. Refresh affected order statuses
    //
    // COMMIT
    @Transactional
    public void restockBook(Long bookId, int quantity) {
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Book ID must be positive");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }

        // Lock the book to prevent concurrent inventory changes.
        Book book = bookRepository
                .findByIdForInventoryUpdate(bookId)
                .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));

        if (book.isArchived()) {
            throw new IllegalArgumentException("Cannot restock an archived book");
        }

        // Increase available stock.
        bookRepository.increaseStock(bookId, quantity);

        // Fulfill outstanding demand, oldest orders first.
        inventoryAllocationService.allocateOutstandingForBook(bookId);
    }
}
