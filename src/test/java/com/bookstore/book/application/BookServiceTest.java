package com.bookstore.book.application;

import com.bookstore.book.api.dto.BookResponse;
import com.bookstore.book.api.dto.CreateBookRequest;
import com.bookstore.book.api.dto.UpdateBookRequest;
import com.bookstore.book.application.exception.BookAlreadyExistsException;
import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.category.application.port.CategoryRepository;
import com.bookstore.category.domain.Category;
import com.bookstore.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final long BOOK_ID = 10L;
    private static final String ISBN = "9780141182803";
    private static final String OTHER_ISBN = "9780141187761";
    private static final BigDecimal PRICE = new BigDecimal("12.50");
    private static final Category FICTION = new Category(1L, "Fiction", null);
    private static final Category CLASSICS = new Category(2L, "Classics", null);

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void createBook_shouldSaveBookAndAssociateCategories() {
        List<Long> categoryIds = List.of(FICTION.getId());
        when(categoryRepository.findById(FICTION.getId())).thenReturn(Optional.of(FICTION));
        when(bookRepository.save(any(Book.class))).thenReturn(book("Original title", null));
        when(bookRepository.findCategoriesByBookIds(List.of(BOOK_ID)))
                .thenReturn(Map.of(BOOK_ID, List.of(FICTION)));

        BookResponse response = bookService.createBook(createRequest(categoryIds));

        ArgumentCaptor<Book> saved = ArgumentCaptor.forClass(Book.class);
        var writes = inOrder(bookRepository);
        writes.verify(bookRepository).save(saved.capture());
        writes.verify(bookRepository).replaceCategories(BOOK_ID, categoryIds);
        assertEquals("Original title", saved.getValue().getTitle());
        assertEquals("Author", saved.getValue().getAuthor());
        assertEquals(ISBN, saved.getValue().getIsbn());
        assertEquals(7, saved.getValue().getStock());
        assertEquals(BOOK_ID, response.getId());
        assertEquals(FICTION.getId(), response.getCategories().getFirst().getId());
    }

    @Test
    void createBook_shouldRejectExistingIsbnBeforeWriting() {
        when(bookRepository.existsByIsbn(ISBN)).thenReturn(true);

        assertThrows(BookAlreadyExistsException.class,
                () -> bookService.createBook(createRequest(List.of())));

        verifyNoCreateWrites();
    }

    @Test
    void createBook_shouldRejectDuplicateCategoriesBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> bookService.createBook(createRequest(List.of(FICTION.getId(), FICTION.getId()))));

        verifyNoCreateWrites();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createBook_shouldRejectUnknownCategoryBeforeWriting() {
        when(categoryRepository.findById(FICTION.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> bookService.createBook(createRequest(List.of(FICTION.getId()))));

        verifyNoCreateWrites();
    }

    @Test
    void createBook_shouldTranslateDuplicateKeyFromSave() {
        DuplicateKeyException duplicateKey = new DuplicateKeyException("Duplicate ISBN");
        when(bookRepository.save(any(Book.class))).thenThrow(duplicateKey);

        var error = assertThrows(BookAlreadyExistsException.class,
                () -> bookService.createBook(createRequest(List.of())));

        assertSame(duplicateKey, error.getCause());
        verify(bookRepository, never()).replaceCategories(any(), any());
    }

    @Test
    void updateBook_shouldUpdateSuppliedMetadataAndPreserveOtherFieldsAndStock() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book("Original title", null)));
        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book("New title", null)));
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle(" New title ");

        BookResponse response = bookService.updateBook(BOOK_ID, request);

        ArgumentCaptor<Book> updated = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).updateMetadata(updated.capture());
        assertEquals("New title", updated.getValue().getTitle());
        assertEquals("Author", updated.getValue().getAuthor());
        assertEquals(ISBN, updated.getValue().getIsbn());
        assertEquals(PRICE, updated.getValue().getPrice());
        assertEquals(7, updated.getValue().getStock());
        assertEquals("New title", response.getTitle());
        verify(bookRepository, never()).replaceCategories(any(), any());
    }

    @Test
    void updateBook_shouldReplaceCategoriesWhenTheyChange() {
        Book existing = book("Original title", null);
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(existing));
        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(CLASSICS.getId())).thenReturn(Optional.of(CLASSICS));
        when(bookRepository.findCategoriesByBookIds(List.of(BOOK_ID)))
                .thenReturn(Map.of(BOOK_ID, List.of(FICTION)))
                .thenReturn(Map.of(BOOK_ID, List.of(CLASSICS)));
        UpdateBookRequest request = new UpdateBookRequest();
        request.setCategoryIds(List.of(CLASSICS.getId()));

        BookResponse response = bookService.updateBook(BOOK_ID, request);

        verify(bookRepository).replaceCategories(BOOK_ID, List.of(CLASSICS.getId()));
        assertEquals(CLASSICS.getId(), response.getCategories().getFirst().getId());
    }

    @Test
    void updateBook_shouldSkipWritesWhenOnlyCategoryOrderChanges() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book("Original title", null)));
        when(bookRepository.findCategoriesByBookIds(List.of(BOOK_ID)))
                .thenReturn(Map.of(BOOK_ID, List.of(FICTION, CLASSICS)));
        when(categoryRepository.findById(FICTION.getId())).thenReturn(Optional.of(FICTION));
        when(categoryRepository.findById(CLASSICS.getId())).thenReturn(Optional.of(CLASSICS));
        UpdateBookRequest request = new UpdateBookRequest();
        request.setCategoryIds(List.of(CLASSICS.getId(), FICTION.getId()));

        BookResponse response = bookService.updateBook(BOOK_ID, request);

        assertEquals(2, response.getCategories().size());
        verify(bookRepository, never()).updateMetadata(any());
        verify(bookRepository, never()).replaceCategories(any(), any());
    }

    @Test
    void updateBook_shouldRejectConflictingIsbnBeforeWriting() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book("Original title", null)));
        when(bookRepository.existsByIsbn(OTHER_ISBN)).thenReturn(true);
        UpdateBookRequest request = new UpdateBookRequest();
        request.setIsbn(OTHER_ISBN);

        assertThrows(BookAlreadyExistsException.class, () -> bookService.updateBook(BOOK_ID, request));

        verify(bookRepository, never()).updateMetadata(any());
        verify(bookRepository, never()).replaceCategories(any(), any());
    }

    @Test
    void archiveBook_shouldArchiveActiveBook() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book("Original title", null)));

        bookService.archiveBook(BOOK_ID);

        verify(bookRepository).archiveById(BOOK_ID);
    }

    @Test
    void archiveBook_shouldRejectBookWithoutActiveMatch() {
        assertThrows(NoSuchElementException.class, () -> bookService.archiveBook(BOOK_ID));

        verify(bookRepository, never()).archiveById(any());
    }

    @Test
    void restoreBook_shouldRestoreArchivedBook() {
        Book archived = book("Original title", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(bookRepository.findArchivedByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(archived));

        bookService.restoreBook(BOOK_ID);

        verify(bookRepository).restoreById(BOOK_ID);
    }

    @Test
    void restoreBook_shouldRejectBookWithoutArchivedMatch() {
        assertThrows(NoSuchElementException.class, () -> bookService.restoreBook(BOOK_ID));

        verify(bookRepository, never()).restoreById(any());
    }

    @Test
    void getBooks_shouldApplyFiltersAndReturnPageMetadata() {
        when(bookRepository.findAll("Original", "Author", FICTION.getId(), 10, 20L))
                .thenReturn(List.of(book("Original title", null)));
        when(bookRepository.count("Original", "Author", FICTION.getId())).thenReturn(21L);
        when(bookRepository.findCategoriesByBookIds(List.of(BOOK_ID)))
                .thenReturn(Map.of(BOOK_ID, List.of(FICTION)));

        PageResponse<BookResponse> result = bookService.getBooks("Original", "Author", FICTION.getId(), 2, 10);

        assertEquals(2, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(21, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getContent().size());
        assertEquals(BOOK_ID, result.getContent().getFirst().getId());
        assertEquals(FICTION.getId(), result.getContent().getFirst().getCategories().getFirst().getId());
    }

    @ParameterizedTest
    @CsvSource({"-1, 10, 1", "0, 0, 1", "0, 101, 1", "0, 10, 0"})
    void getBooks_shouldRejectInvalidArguments(int page, int size, long categoryId) {
        assertThrows(IllegalArgumentException.class, () -> bookService.getBooks(null, null, categoryId, page, size));

        verifyNoInteractions(bookRepository);
    }

    @Test
    void getBook_shouldReturnBookWithCategories() {
        when(bookRepository.findById(BOOK_ID)).thenReturn(Optional.of(book("Original title", null)));
        when(bookRepository.findCategoriesByBookIds(List.of(BOOK_ID)))
                .thenReturn(Map.of(BOOK_ID, List.of(FICTION)));

        BookResponse response = bookService.getBook(BOOK_ID);

        assertEquals(BOOK_ID, response.getId());
        assertEquals("Original title", response.getTitle());
        assertEquals(FICTION.getId(), response.getCategories().getFirst().getId());
    }

    @Test
    void getBook_shouldRejectBookWithoutActiveMatch() {
        assertThrows(NoSuchElementException.class, () -> bookService.getBook(BOOK_ID));

        verify(bookRepository, never()).findCategoriesByBookIds(any());
    }

    private void verifyNoCreateWrites() {
        verify(bookRepository, never()).save(any());
        verify(bookRepository, never()).replaceCategories(any(), any());
    }

    private static CreateBookRequest createRequest(List<Long> categoryIds) {
        return new CreateBookRequest(" Original title ", " Author ", ISBN, null, PRICE, 7, null, categoryIds);
    }

    private static Book book(String title, OffsetDateTime archivedAt) {
        return new Book(BOOK_ID, title, "Author", ISBN, null, PRICE, 7, null, null, null, archivedAt);
    }
}
