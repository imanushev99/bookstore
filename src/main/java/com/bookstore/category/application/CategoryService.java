package com.bookstore.category.application;

import com.bookstore.category.api.dto.CategoryResponse;
import com.bookstore.category.api.dto.CreateCategoryRequest;
import com.bookstore.category.api.dto.UpdateCategoryRequest;
import com.bookstore.category.application.exception.CategoryAlreadyExistsException;
import com.bookstore.category.application.port.CategoryRepository;
import com.bookstore.category.domain.Category;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Category not found"));

        return toResponse(category);
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = request.getName().trim();

        Category category = new Category(null, name, request.getDescription());

        try {
            Category savedCategory = categoryRepository.save(category);
            return toResponse(savedCategory);
        } catch (DuplicateKeyException exception) {
            throw new CategoryAlreadyExistsException("Category with name '" + name + "' already exists", exception);
        }
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category existing = categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Category not found"));

        if (request.getName() == null && request.getDescription() == null) {
            throw new IllegalArgumentException("At least one field must be provided");
        }

        String name = existing.getName();
        String description = existing.getDescription();

        if (request.getName() != null) {
            name = request.getName().trim();

            if (name.isEmpty()) {
                throw new IllegalArgumentException("Category name cannot be blank");
            }
        }

        if (request.getDescription() != null) {
            description = request.getDescription();
        }

        if (Objects.equals(existing.getName(), name) && Objects.equals(existing.getDescription(), description)) {
            return toResponse(existing);
        }

        Category updated = new Category(id, name, description);

        try {
            categoryRepository.update(updated);
            return toResponse(updated);
        } catch (DuplicateKeyException exception) {
            throw new CategoryAlreadyExistsException("Category with name '" + name + "' already exists", exception);
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (categoryRepository.findByIdForUpdate(id).isEmpty()) {
            return;
        }

        categoryRepository.deleteById(id);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }
}
