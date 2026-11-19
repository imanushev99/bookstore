package com.bookstore.category.api;

import com.bookstore.category.api.dto.CategoryResponse;
import com.bookstore.category.api.dto.CreateCategoryRequest;
import com.bookstore.category.api.dto.UpdateCategoryRequest;
import com.bookstore.category.application.CategoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        log.info("action=getAllCategories");
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable Long id) {
        log.info("action=getCategory, categoryId={}", id);
        return ResponseEntity.ok(categoryService.getCategory(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        log.info("action=createCategory");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(categoryService.createCategory(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        log.info("action=updateCategory, categoryId={}", id);
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        log.info("action=deleteCategory, categoryId={}", id);
        categoryService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }
}
