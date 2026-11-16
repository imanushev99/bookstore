
package com.bookstore.order.api.dto;

import java.math.BigDecimal;

public class OrderItemResponse {

    private final Long id;
    private final Long bookId;
    private final int quantity;
    private final int allocatedQuantity;
    private final int backorderedQuantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;

    public OrderItemResponse(
            Long id,
            Long bookId,
            int quantity,
            int allocatedQuantity,
            int backorderedQuantity,
            BigDecimal unitPrice) {
        this.id = id;
        this.bookId = bookId;
        this.quantity = quantity;
        this.allocatedQuantity = allocatedQuantity;
        this.backorderedQuantity = backorderedQuantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }

    public Long getId() {
        return id;
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
        return subtotal;
    }
}
