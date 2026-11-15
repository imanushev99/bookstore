package com.bookstore.book.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Book {

    private final Long id;
    private final String title;
    private final String author;
    private final String isbn;
    private final String description;
    private final BigDecimal price;
    private final int stock;
    private final String coverImageUrl;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime archivedAt;

    public Book(
            Long id,
            String title,
            String author,
            String isbn,
            String description,
            BigDecimal price,
            int stock,
            String coverImageUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime archivedAt) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.coverImageUrl = coverImageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
