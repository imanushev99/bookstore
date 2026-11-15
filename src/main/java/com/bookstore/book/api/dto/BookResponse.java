package com.bookstore.book.api.dto;

import com.bookstore.category.api.dto.CategoryResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Clarification: archivedAt is not included in the response DTO since if archivedAt == false,
 *                then the book is included in the response, which is self-explanatory.
 */
public class BookResponse {

    private final Long id;
    private final String title;
    private final String author;
    private final String isbn;
    private final String description;
    private final BigDecimal price;
    private final int stock;
    private final String coverImageUrl;
    private final List<CategoryResponse> categories;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public BookResponse(
            Long id,
            String title,
            String author,
            String isbn,
            String description,
            BigDecimal price,
            int stock,
            String coverImageUrl,
            List<CategoryResponse> categories,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.coverImageUrl = coverImageUrl;
        this.categories = List.copyOf(categories); // unmodifiable copy of the list; categories themselves are mutable, but not the list structure
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public List<CategoryResponse> getCategories() {
        return categories;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
