package com.bookstore.category.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateCategoryRequest extends AbstractCategory {

    public CreateCategoryRequest() {
        super();
    }

    public CreateCategoryRequest(String name, String description) {
        super(name, description);
    }

    @Override
    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name cannot exceed 100 characters")
    public String getName() {
        return super.getName();
    }
}
