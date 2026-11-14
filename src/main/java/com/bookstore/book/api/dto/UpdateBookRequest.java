package com.bookstore.book.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Clarification: Stock handling is separate from the ordinary book metadata,
 *                because stock can be modified from multiple operations in the app at the same time,
 *                and it would be easier to handle separately.
 */
public class UpdateBookRequest {
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    @Pattern(regexp = "(?s).*\\S.*", message = "Title cannot be blank")
    private String title;

    @Size(max = 255, message = "Author cannot exceed 255 characters")
    @Pattern(regexp = "(?s).*\\S.*", message = "Author cannot be blank")
    private String author;

    private String description;

    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 4, fraction = 2)
    private BigDecimal price;

    private String coverImageUrl;

    private List<@NotNull @Positive Long> categoryIds;

    public UpdateBookRequest() {
    }

    public UpdateBookRequest(
            String title,
            String author,
            String description,
            BigDecimal price,
            String coverImageUrl,
            List<Long> categoryIds) {
        this.title = title;
        this.author = author;
        this.description = description;
        this.price = price;
        this.coverImageUrl = coverImageUrl;
        this.categoryIds = categoryIds;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public List<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds;
    }
}
