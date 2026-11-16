
package com.bookstore.category.application;

import com.bookstore.category.api.dto.CategoryResponse;
import com.bookstore.category.api.dto.CreateCategoryRequest;
import com.bookstore.category.api.dto.UpdateCategoryRequest;
import com.bookstore.category.application.exception.CategoryAlreadyExistsException;
import com.bookstore.category.application.exception.CategoryInUseException;
import com.bookstore.category.application.port.CategoryRepository;
import com.bookstore.category.domain.Category;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void getAllCategories_shouldReturnCategories() {
        // Arrange
        Category fiction = new Category(1L, "Fiction", "Fiction books");

        Category history = new Category(2L, "History", "History books");

        when(categoryRepository.findAll()).thenReturn(List.of(fiction, history));

        // Act
        List<CategoryResponse> result = categoryService.getAllCategories();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Fiction", result.get(0).getName());
        assertEquals("History", result.get(1).getName());

        verify(categoryRepository).findAll();
    }

    @Test
    void getCategory_shouldReturnCategory() {
        // Arrange
        Category category = new Category(1L, "Fiction", "Fiction books");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        // Act
        CategoryResponse result = categoryService.getCategory(1L);

        // Assert
        assertEquals(1L, result.getId());
        assertEquals("Fiction", result.getName());
        assertEquals("Fiction books", result.getDescription());
    }

    @Test
    void getCategory_shouldThrowWhenNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> categoryService.getCategory(99L));
    }

    @Test
    void createCategory_shouldCreateSuccessfully() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Fiction", "Fiction books");

        Category saved = new Category(1L, "Fiction", "Fiction books");

        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        // Act
        CategoryResponse result = categoryService.createCategory(request);

        // Assert
        assertEquals(1L, result.getId());
        assertEquals("Fiction", result.getName());

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_shouldRejectDuplicateName() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("Fiction", "Description");

        when(categoryRepository.existsByName("Fiction")).thenReturn(true);

        // Act & Assert
        assertThrows(CategoryAlreadyExistsException.class, () -> categoryService.createCategory(request));

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_shouldUpdateSuccessfully() {
        // Arrange
        Category existing = new Category(1L, "Fiction", "Old description");

        UpdateCategoryRequest request = new UpdateCategoryRequest("Fantasy", "Updated description");

        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        // Act
        CategoryResponse result = categoryService.updateCategory(1L, request);

        // Assert
        assertEquals("Fantasy", result.getName());
        assertEquals("Updated description", result.getDescription());

        verify(categoryRepository).update(any(Category.class));
    }

    @Test
    void deleteCategory_shouldDeleteSuccessfully() {
        // Arrange
        Category category = new Category(1L, "Fiction", "Fiction books");

        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(category));

        // Act
        categoryService.deleteCategory(1L);

        // Assert
        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteCategory_shouldRejectCategoryInUse() {
        // Arrange
        Category category = new Category(1L, "Fiction", "Fiction books");

        when(categoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(category));

        doThrow(new DataIntegrityViolationException("Category is referenced by books"))
                .when(categoryRepository).deleteById(1L);

        // Act & Assert
        assertThrows(CategoryInUseException.class, () -> categoryService.deleteCategory(1L));
    }
}
