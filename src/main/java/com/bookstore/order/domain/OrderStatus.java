package com.bookstore.order.domain;

public enum OrderStatus {
    PENDING,
    PROCESSING,
    CONFIRMED,
    BACKORDERED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
