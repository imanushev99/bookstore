
package com.bookstore.order.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Order {

    private final Long id;
    private final Long userId;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public Order(
            Long id,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
