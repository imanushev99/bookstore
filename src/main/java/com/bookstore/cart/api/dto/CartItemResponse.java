
package com.bookstore.cart.api.dto;

import java.math.BigDecimal;

public class CartItemResponse {

    private final Long bookId;
    private final String title;
    private final String author;
    private final String isbn;
    private final BigDecimal price;
    private final int quantity;
    private final BigDecimal subtotal;
    private final String coverImageUrl;

    public CartItemResponse(
            Long bookId,
            String title,
            String author,
            String isbn,
            BigDecimal price,
            int quantity,
            String coverImageUrl
    ) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = price.multiply(BigDecimal.valueOf(quantity));
        this.coverImageUrl = coverImageUrl;
    }

    public Long getBookId() {
        return bookId;
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

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }
}
