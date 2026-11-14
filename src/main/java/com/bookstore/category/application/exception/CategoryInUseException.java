package com.bookstore.category.application.exception;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
