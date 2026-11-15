package com.bookstore.idempotency.application.exception;

public class IdempotencyRequestInProgressException extends RuntimeException {
    public IdempotencyRequestInProgressException(String message) {
        super((message));
    }
}
