package com.bookstore.category.api.dto;

public class CategoryResponse extends AbstractCategory {

    private final Long id;

    public CategoryResponse(Long id, String name, String description) {
        super(name, description);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
