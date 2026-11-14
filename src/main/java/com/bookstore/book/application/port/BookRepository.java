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
            long offset
    );

    long count(
            String title,
            String author,
            Long categoryId
    );

    Optional<Book> findById(Long id);

    Optional<Book> findByIdForUpdate(Long id);

    Book save(Book book);

    void updateMetadata(Book book);

    // Retrieve an archived book and lock its row
    // ordinary retrieve is filtering them out
    Optional<Book> findArchivedByIdForUpdate(Long id);

    void archiveById(Long id);

    // Restore an archived book
    void restoreById(Long id);

    Map<Long, List<Category>> findCategoriesByBookIds(List<Long> bookIds);

    void replaceCategories(Long bookId, List<Long> categoryIds);
}
