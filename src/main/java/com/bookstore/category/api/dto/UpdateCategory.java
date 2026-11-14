package com.bookstore.category.api.dto;

import jakarta.validation.constraints.Size;

public class UpdateCategory extends AbstractCategory {

    public UpdateCategory() {
        super();
    }

    public UpdateCategory(String name, String description) {
        super(name, description);
    }

    @Override
    @Size(max = 100, message = "Category name cannot exceed 100 characters")
    public String getName() {
        return super.getName();
    }
}
