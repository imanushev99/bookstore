
package com.bookstore.order.domain;

import java.math.BigDecimal;

public class OrderItem {

    private final Long id;
    private final Long orderId;
    private final Long bookId;
    private final int quantity;
    private final int allocatedQuantity;
    private final int backorderedQuantity;
    private final BigDecimal unitPrice;

    public OrderItem(
            Long id,
            Long orderId,
            Long bookId,
            int quantity,
            int allocatedQuantity,
            int backorderedQuantity,
            BigDecimal unitPrice) {
        this.id = id;
        this.orderId = orderId;
        this.bookId = bookId;
        this.quantity = quantity;
        this.allocatedQuantity = allocatedQuantity;
        this.backorderedQuantity = backorderedQuantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getBookId() {
        return bookId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public int getBackorderedQuantity() {
        return backorderedQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }
}
