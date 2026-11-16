package com.bookstore.cart.domain;

import java.time.OffsetDateTime;

public class Cart {
    private final Long id;
    private final Long userId;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public Cart(
            Long id,
            Long userId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
