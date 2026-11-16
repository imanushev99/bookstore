package com.bookstore.book.application.port;

import com.bookstore.book.domain.Book;
import com.bookstore.category.domain.Category;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface BookRepository {

    // title, author, categoryId are optional
    List<Book> findAll(
            String title,
            String author,
            Long categoryId,
            int limit,
            long offset);

    long count(String title, String author, Long categoryId);

    Optional<Book> findById(Long id);

    Optional<Book> findByIdForUpdate(Long id);

    // prevents an admin from archiving a book while its checkout is in progress; used in OrderService
    Optional<Book> findByIdForShare(Long id);

    // Retrieve an archived book and lock its row
    // ordinary retrieve is filtering them out
    Optional<Book> findArchivedByIdForUpdate(Long id);

    // user in cart service when a book that is in someone's cart is archived
    Optional<Book> findArchivedById(Long id);

    boolean existsByIsbn(String isbn);

    Book save(Book book);

    void updateMetadata(Book book);

    void archiveById(Long id);

    // Restore an archived book
    void restoreById(Long id);

    // used in BookResponse, since it contains full information of categories, not just ID's
    Map<Long, List<Category>> findCategoriesByBookIds(List<Long> bookIds);

    // used in updating book categories
    void replaceCategories(Long bookId, List<Long> categoryIds);

    // used in OrderProcessingService

    Optional<Book> findByIdForInventoryUpdate(Long bookId);

    void decreaseStock(Long bookId, int quantity);

    void increaseStock(Long bookId, int quantity);
}
