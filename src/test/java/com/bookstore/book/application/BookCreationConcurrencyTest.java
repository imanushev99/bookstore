package com.bookstore.book.application;

import com.bookstore.book.api.dto.BookResponse;
import com.bookstore.book.api.dto.CreateBookRequest;
import com.bookstore.book.application.exception.BookAlreadyExistsException;
import com.bookstore.book.infrastructure.JdbcBookRepository;
import com.bookstore.category.infrastructure.JdbcCategoryRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(BookCreationConcurrencyTest.TestConfig.class)
class BookCreationConcurrencyTest {

    private static final String ISBN = "9780141182803";

    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private BookService bookService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcTransactionManager transactionManager;

    @MockitoSpyBean
    private JdbcBookRepository bookRepository;

    @Test
    void concurrentCreatesWithSameIsbn_shouldCommitExactlyOneBook() throws Exception {
        CyclicBarrier bothCheckedIsbn = new CyclicBarrier(2);
        doAnswer(invocation -> {
            boolean exists = (boolean) invocation.callRealMethod();
            assertFalse(exists, "Both transactions should see an unused ISBN");
            bothCheckedIsbn.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(bookRepository).existsByIsbn(ISBN);

        // This insert commits before the worker transactions start.
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, "Fiction");

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<CreationResult> first = workers.submit(() -> createBook("First book", categoryId));
            Future<CreationResult> second = workers.submit(() -> createBook("Second book", categoryId));
            List<CreationResult> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            List<BookResponse> created = results.stream().map(CreationResult::book).filter(book -> book != null).toList();
            List<BookAlreadyExistsException> conflicts = results.stream()
                    .map(CreationResult::conflict)
                    .filter(conflict -> conflict != null)
                    .toList();

            assertEquals(1, created.size());
            assertEquals(1, conflicts.size());
            assertInstanceOf(DuplicateKeyException.class, conflicts.getFirst().getCause());
            assertEquals(List.of(created.getFirst().getId()),
                    jdbc.queryForList("SELECT id FROM books WHERE isbn = ?", Long.class, ISBN));
            assertEquals(List.of(categoryId), jdbc.queryForList(
                    "SELECT category_id FROM book_categories WHERE book_id = ?", Long.class, created.getFirst().getId()));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS), "Book creation workers did not terminate");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM book_categories WHERE category_id = ?", categoryId);
                jdbc.update("DELETE FROM books WHERE isbn = ?", ISBN);
                jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
            });
        }
    }

    private CreationResult createBook(String title, Long categoryId) {
        CreateBookRequest request = new CreateBookRequest(
                title, "Test author", ISBN, null, new BigDecimal("12.50"), 1, null, List.of(categoryId));
        try {
            return new CreationResult(bookService.createBook(request), null);
        } catch (BookAlreadyExistsException conflict) {
            return new CreationResult(null, conflict);
        }
    }

    private record CreationResult(BookResponse book, BookAlreadyExistsException conflict) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({BookService.class, JdbcBookRepository.class, JdbcCategoryRepository.class})
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }

        @Bean(initMethod = "migrate")
        Flyway flyway(DataSource dataSource) {
            return Flyway.configure().dataSource(dataSource).load();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(10);
            return jdbc;
        }

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbc) {
            return new NamedParameterJdbcTemplate(jdbc);
        }

        @Bean
        JdbcTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }
}
