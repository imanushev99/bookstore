package com.bookstore.book.api;

import com.bookstore.book.api.dto.BookResponse;
import com.bookstore.book.api.dto.CreateBookRequest;
import com.bookstore.book.api.dto.UpdateBookRequest;
import com.bookstore.book.application.BookService;
import com.bookstore.common.api.PageResponse;
import com.bookstore.common.security.SecurityUtils;
import com.bookstore.idempotency.application.IdempotencyService;
import com.bookstore.idempotency.application.RequestHasher;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final BookService bookService;
    private final IdempotencyService idempotencyService;
    private final RequestHasher requestHasher;

    public BookController(BookService bookService, IdempotencyService idempotencyService, RequestHasher requestHasher) {
        this.bookService = bookService;
        this.idempotencyService = idempotencyService;
        this.requestHasher = requestHasher;
    }
    @GetMapping
    public ResponseEntity<PageResponse<BookResponse>> getBooks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("action=getBooks, categoryId={}, page={}, size={}", categoryId, page, size);
        return ResponseEntity.ok(bookService.getBooks(title, author, categoryId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getBook(@PathVariable Long id) {
        log.info("action=getBook, bookId={}", id);
        return ResponseEntity.ok(bookService.getBook(id));
    }

    @PostMapping
    public ResponseEntity<?> createBook(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateBookRequest request) {
        log.info("action=createBook, categoryIds={}, stock={}", request.getCategoryIds(), request.getStock());
        Long userId = SecurityUtils.getCurrentUserId();

        String requestHash = requestHasher.hash("POST /api/books", request);

        // the lambda expression is executed only if the idempotency key was successfully claimed
        return idempotencyService.execute(
                userId,
                IdempotencyOperation.CREATE_BOOK,
                idempotencyKey,
                requestHash,
                () -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(bookService.createBook(request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request) {
        log.info("action=updateBook, bookId={}", id);
        return ResponseEntity.ok(bookService.updateBook(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveBook(@PathVariable Long id) {
        log.info("action=archiveBook, bookId={}", id);
        bookService.archiveBook(id);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<Void> restoreBook(@PathVariable Long id) {
        log.info("action=restoreBook, bookId={}", id);
        bookService.restoreBook(id);

        return ResponseEntity.noContent().build();
    }
}
