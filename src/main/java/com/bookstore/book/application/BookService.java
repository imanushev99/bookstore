package com.bookstore.book.application;

import com.bookstore.book.api.dto.BookResponse;
import com.bookstore.book.api.dto.CreateBookRequest;
import com.bookstore.book.api.dto.UpdateBookRequest;
import com.bookstore.book.application.exception.BookAlreadyExistsException;
import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.category.api.dto.CategoryResponse;
import com.bookstore.category.application.port.CategoryRepository;
import com.bookstore.category.domain.Category;
import com.bookstore.common.api.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    public BookService(BookRepository bookRepository, CategoryRepository categoryRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookResponse> getBooks(String title, String author, Long categoryId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException("Category ID must be positive");
        }

        long offset = (long) page * size;

        List<Book> books = bookRepository.findAll(title, author, categoryId, size, offset);

        long total = bookRepository.count(title, author, categoryId);

        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .toList();

        // because BookResponse includes categories as objects, not just ID's
        Map<Long, List<Category>> categories = bookRepository.findCategoriesByBookIds(bookIds);

        List<BookResponse> content = books.stream()
                .map(book -> toResponse(book, categories.getOrDefault(book.getId(), List.of())))
                .toList();

        return new PageResponse<>(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public BookResponse getBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Book not found"));

        List<Category> categories = getBookCategories(id);

        return toResponse(book, categories);
    }

    @Transactional
    public BookResponse createBook(CreateBookRequest request) {
        String isbn = request.getIsbn();

        if (bookRepository.existsByIsbn(isbn)) {
            throw new BookAlreadyExistsException("A book with ISBN " + isbn + " already exists");
        }

        validateCategoryIds(request.getCategoryIds());

        Book book = new Book(
                null,
                request.getTitle().trim(),
                request.getAuthor().trim(),
                isbn,
                request.getDescription(),
                request.getPrice(),
                request.getStock(),
                request.getCoverImageUrl(),
                null,
                null,
                null);

        Book savedBook;

        try {
            savedBook = bookRepository.save(book);
        } catch (DuplicateKeyException exception) {
            throw new BookAlreadyExistsException("A book with ISBN " + isbn + " already exists", exception);
        }

        bookRepository.replaceCategories(savedBook.getId(), request.getCategoryIds());

        return toResponse(savedBook, getBookCategories(savedBook.getId()));
    }

    @Transactional
    public BookResponse updateBook(Long id, UpdateBookRequest request) {
        Book existing = bookRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Book not found"));

        // Check if metadata changed:

        String title = request.getTitle() != null
                ? request.getTitle().trim()
                : existing.getTitle();

        String author = request.getAuthor() != null
                ? request.getAuthor().trim()
                : existing.getAuthor();

        String isbn = request.getIsbn() != null
                ? request.getIsbn().trim()
                : existing.getIsbn();

        String description = request.getDescription() != null
                ? request.getDescription()
                : existing.getDescription();

        BigDecimal price = request.getPrice() != null
                ? request.getPrice()
                : existing.getPrice();

        String coverImageUrl = request.getCoverImageUrl() != null
                ? request.getCoverImageUrl()
                : existing.getCoverImageUrl();

        Book updated = new Book(
                id,
                title,
                author,
                isbn,
                description,
                price,
                existing.getStock(),
                coverImageUrl,
                existing.getCreatedAt(),
                existing.getUpdatedAt(),
                existing.getArchivedAt());

        boolean isbnChanged = !existing.getIsbn().equals(isbn);

        if (isbnChanged && bookRepository.existsByIsbn(isbn)) {
            throw new BookAlreadyExistsException("A book with ISBN " + isbn + " already exists");
        }

        // stock shouldn't be updated here, because this relates only to metadata => new endpoint for configuring stock
        boolean metadataChanged = !Objects.equals(existing.getTitle(), title)
                                    || !Objects.equals(existing.getAuthor(), author)
                                    || isbnChanged
                                    || !Objects.equals(existing.getDescription(), description)
                                    || existing.getPrice().compareTo(price) != 0
                                    || !Objects.equals(existing.getCoverImageUrl(), coverImageUrl);

        // Check if categories changed:

        List<Category> existingCategories = getBookCategories(id);

        boolean categoriesChanged = false;

        if (request.getCategoryIds() != null) {
            validateCategoryIds(request.getCategoryIds());

            Set<Long> existingIds = new HashSet<>(
                    existingCategories.stream()
                            .map(Category::getId)
                            .toList());

            categoriesChanged = !existingIds.equals(new HashSet<>(request.getCategoryIds()));
        }

        if (!metadataChanged && !categoriesChanged) {
            return toResponse(existing, existingCategories);
        }

        if (categoriesChanged) {
            bookRepository.replaceCategories(id, request.getCategoryIds());
        }

        // even if only categoriesChanged == true, metadata still includes updated_at which needs to be updated
        try {
            bookRepository.updateMetadata(updated);
        } catch (DuplicateKeyException exception) {
            // someone may have created a book with that isbn during this time because the table is not locked
            throw new BookAlreadyExistsException("A book with ISBN " + isbn + " already exists", exception);
        }

        Book refreshed = bookRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Book not found"));

        // categories need to be extracted because we have their IDs only in the request
        return toResponse(refreshed, getBookCategories(id));
    }

    @Transactional
    public void archiveBook(Long id) {
        bookRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Active book not found"));

        bookRepository.archiveById(id);
    }

    @Transactional
    public void restoreBook(Long id) {
        bookRepository.findArchivedByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Archived book not found"));

        bookRepository.restoreById(id);
    }

    private void validateCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null) {
            throw new IllegalArgumentException("Category IDs are required");
        }

        //validate no duplication in the input

        Set<Long> categoryIdsSet = new HashSet<>(categoryIds);

        if (categoryIdsSet.size() != categoryIds.size()) {
            throw new IllegalArgumentException("Duplicate category IDs are not allowed");
        }

        // validate existing categories

        for (Long categoryId : categoryIds) {
            if(categoryRepository.findById(categoryId).isEmpty()) {
                throw new IllegalArgumentException("Category with id=" + categoryId + " does not exist.");

            }
        }
    }

    private List<Category> getBookCategories(Long bookId) {
        return bookRepository.findCategoriesByBookIds(List.of(bookId))
                .getOrDefault(bookId, List.of());
    }

    private BookResponse toResponse(Book book, List<Category> categories) {
        List<CategoryResponse> categoryResponses =
                categories.stream()
                        .map(category -> new CategoryResponse(
                                category.getId(),
                                category.getName(),
                                category.getDescription()))
                        .toList();

        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getDescription(),
                book.getPrice(),
                book.getStock(),
                book.getCoverImageUrl(),
                categoryResponses,
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}
