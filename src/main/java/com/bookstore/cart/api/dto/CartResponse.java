
package com.bookstore.cart.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class CartResponse {

    private final Long cartId;
    private final List<CartItemResponse> items;
    private final int totalQuantity;
    private final BigDecimal totalPrice;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public CartResponse(
            Long cartId,
            List<CartItemResponse> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.cartId = cartId;
        this.items = List.copyOf(items);

        this.totalQuantity = items.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        this.totalPrice = items.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getCartId() {
        return cartId;
    }

    public List<CartItemResponse> getItems() {
        return items;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
