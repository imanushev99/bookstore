package com.bookstore.cart.domain;

public class CartItem {
    private final Long id;
    private final Long cartId;
    private final Long bookId;
    private final int quantity;

    public CartItem(
            Long id,
            Long cartId,
            Long bookId,
            int quantity
    ) {
        this.id = id;
        this.cartId = cartId;
        this.bookId = bookId;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Long getBookId() {
        return bookId;
    }

    public int getQuantity() {
        return quantity;
    }
}
