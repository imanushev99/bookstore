
package com.bookstore.order.api.dto;

import com.bookstore.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class OrderResponse {

    private final Long id;
    private final Long userId;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final List<OrderItemResponse> items;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public OrderResponse(
            Long id,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            List<OrderItemResponse> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = List.copyOf(items);
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

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
