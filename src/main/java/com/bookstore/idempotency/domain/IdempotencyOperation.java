package com.bookstore.idempotency.domain;

public enum IdempotencyOperation {

    CREATE_BOOK,
    CREATE_ORDER,
    CANCEL_ORDER,
    RESTOCK_BOOK,
    SHIP_ORDER,
    DELIVER_ORDER
}
