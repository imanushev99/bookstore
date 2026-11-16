package com.bookstore.idempotency.domain;

public enum IdempotencyOperation {

    CREATE_BOOK,
    CREATE_ORDER,
    RESTOCK_BOOK
}
