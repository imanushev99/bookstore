package com.bookstore.category.application.port;

import com.bookstore.category.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    List<Category> findAll();

    Optional<Category> findById(Long id);

    Optional<Category> findByIdForUpdate(Long id);

    boolean existsByName(String name);

    Category save(Category category);

    void update(Category category);

    void deleteById(Long id);
}
