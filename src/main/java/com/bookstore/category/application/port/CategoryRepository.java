package com.bookstore.category.application.port;

import com.bookstore.category.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    List<Category> findAll();

    Optional<Category> findById(Long id);

    Optional<Category> findByIdForUpdate(Long id);

    // Used for id verification when updating a book's categories, instead of calling findById N times.
    List<Long> findExistingIds(List<Long> ids);

    boolean existsByName(String name);

    Category save(Category category);

    void update(Category category);

    void deleteById(Long id);

    // TODO: find books for a category
}
