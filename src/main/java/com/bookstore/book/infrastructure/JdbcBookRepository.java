package com.bookstore.book.infrastructure;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.category.domain.Category;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class JdbcBookRepository implements BookRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBookRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- BOOK RETRIEVAL ----------------

    @Override
    public List<Book> findAll(
            String title,
            String author,
            Long categoryId,
            int limit,
            long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();

        String filters = buildFilters(title, author, categoryId, params);

        String sql = """
                SELECT b.*
                FROM books b
                WHERE
                """ + filters + """
                
                ORDER BY b.title, b.id
                LIMIT :limit OFFSET :offset
                """;

        params.addValue("limit", limit);
        params.addValue("offset", offset);

        return jdbc.query(sql, params, this::mapBook);
    }

    @Override
    public long count(String title, String author, Long categoryId) {
        MapSqlParameterSource params = new MapSqlParameterSource();

        String filters = buildFilters(title, author, categoryId, params);

        String sql = """
                SELECT COUNT(*)
                FROM books b
                WHERE
                """ + filters;

        Long count = jdbc.queryForObject(sql, params, Long.class);

        return count == null ? 0 : count;
    }

    @Override
    public Optional<Book> findById(Long id) {
        String sql = """
                SELECT *
                FROM books
                WHERE id = :id
                  AND archived_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapBook)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Book> findByIdForUpdate(Long id) {
        String sql = """
                SELECT *
                FROM books
                WHERE id = :id
                  AND archived_at IS NULL
                FOR UPDATE NOWAIT
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapBook)
                .stream()
                .findFirst();
    }


    // FOR SHARE allows reads only; multiple transactions can acquire the same lock
    // FOR UPDATE blocks writes and reads for other transactions; only one transaction can acquire the lock
    @Override
    public Optional<Book> findByIdForShare(Long id) {
        String sql = """
            SELECT *
            FROM books
            WHERE id = :id
              AND archived_at IS NULL
            FOR SHARE
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("id", id);

        return jdbc.query(sql, params, this::mapBook)
                .stream()
                .findFirst();
    }


    @Override
    public Optional<Book> findArchivedByIdForUpdate(Long id) {
        String sql = """
                SELECT *
                FROM books
                WHERE id = :id
                  AND archived_at IS NOT NULL
                FOR UPDATE NOWAIT
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapBook)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Book> findArchivedById(Long id) {
        String sql = """
            SELECT *
            FROM books
            WHERE id = :id
              AND archived_at IS NOT NULL
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        List<Book> books = jdbc.query(sql, params, this::mapBook);

        return books.stream().findFirst();
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM books
                WHERE isbn = :isbn
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("isbn", isbn);

        Boolean exists = jdbc.queryForObject(sql, params, Boolean.class);

        return Boolean.TRUE.equals(exists);
    }

    // ---------------- BOOK MODIFICATIONS ----------------

    @Override
    public Book save(Book book) {
        String sql = """
                INSERT INTO books (
                    title,
                    author,
                    isbn,
                    description,
                    price,
                    stock,
                    cover_image_url
                )
                VALUES (
                    :title,
                    :author,
                    :isbn,
                    :description,
                    :price,
                    :stock,
                    :coverImageUrl
                )
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("title", book.getTitle())
                .addValue("author", book.getAuthor())
                .addValue("isbn", book.getIsbn())
                .addValue("description", book.getDescription())
                .addValue("price", book.getPrice())
                .addValue("stock", book.getStock())
                .addValue("coverImageUrl", book.getCoverImageUrl());

        return jdbc.queryForObject(sql, params, this::mapBook);
    }

    @Override
    public void updateMetadata(Book book) {
        String sql = """
                UPDATE books
                SET title = :title,
                    author = :author,
                    isbn = :isbn,
                    description = :description,
                    price = :price,
                    cover_image_url = :coverImageUrl,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND archived_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", book.getId())
                .addValue("title", book.getTitle())
                .addValue("author", book.getAuthor())
                .addValue("isbn", book.getIsbn())
                .addValue("description", book.getDescription())
                .addValue("price", book.getPrice())
                .addValue("coverImageUrl", book.getCoverImageUrl());

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected to update exactly one book");
        }
    }

    @Override
    public void archiveById(Long id) {
        String sql = """
                UPDATE books
                SET archived_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND archived_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected to archive exactly one book");
        }
    }

    @Override
    public void restoreById(Long id) {
        String sql = """
                UPDATE books
                SET archived_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND archived_at IS NOT NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected to restore exactly one book");
        }
    }

    // ---------------- BOOK CATEGORIES ----------------

    @Override
    public Map<Long, List<Category>> findCategoriesByBookIds(List<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                SELECT
                    bc.book_id,
                    c.id AS category_id,
                    c.name,
                    c.description
                FROM book_categories bc
                JOIN categories c ON c.id = bc.category_id
                WHERE bc.book_id IN (:bookIds)
                ORDER BY bc.book_id, c.name, c.id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookIds", bookIds);

        return jdbc.query(sql, params, rs -> {
            Map<Long, List<Category>> result = new HashMap<>();

            while (rs.next()) {
                Long bookId = rs.getLong("book_id");

                Category category = new Category(
                        rs.getLong("category_id"),
                        rs.getString("name"),
                        rs.getString("description")
                );

                result.computeIfAbsent(bookId, key -> new ArrayList<>()).add(category);
            }

            return result;
        });
    }

    @Override
    public void replaceCategories(Long bookId, List<Long> categoryIds) {
        String deleteSql = """
                DELETE FROM book_categories
                WHERE book_id = :bookId
                """;

        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
                        .addValue("bookId", bookId);

        jdbc.update(deleteSql, deleteParams);

        if (categoryIds.isEmpty()) {
            return;
        }

        String insertSql = """
                INSERT INTO book_categories (book_id, category_id)
                VALUES (:bookId, :categoryId)
                """;

        MapSqlParameterSource[] batch = new MapSqlParameterSource[categoryIds.size()];

        for (int i = 0; i < categoryIds.size(); i++) {
            batch[i] = new MapSqlParameterSource()
                    .addValue("bookId", bookId)
                    .addValue("categoryId", categoryIds.get(i));
        }

        jdbc.batchUpdate(insertSql, batch);
    }

    // use FOR UPDATE instead of FOR UPDATE NOWAIT,
    // allowing concurrent Kafka consumers to wait for the book lock rather than immediately fail.
    // also, include archived books because previously accepted orders may still require inventory allocation
    @Override
    public Optional<Book> findByIdForInventoryUpdate(Long bookId) {
        String sql = """
            SELECT *
            FROM books
            WHERE id = :bookId
            FOR UPDATE
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("bookId", bookId);

        return jdbc.query(sql, params, this::mapBook)
                .stream()
                .findFirst();
    }


    @Override
    public void decreaseStock(Long bookId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        String sql = """
            UPDATE books
            SET stock = stock - :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :bookId
              AND stock >= :quantity
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookId", bookId)
                .addValue("quantity", quantity);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Insufficient stock or book not found: " + bookId);
        }
    }


    @Override
    public void increaseStock(Long bookId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Restock quantity must be positive");
        }

        String sql = """
            UPDATE books
            SET stock = stock + :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :bookId
              AND archived_at IS NULL
              AND stock <= 2147483647 - :quantity
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookId", bookId)
                .addValue("quantity", quantity);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Unable to restock book: " + bookId);
        }
    }



    // ---------------- HELPERS ----------------

    private String buildFilters(
            String title,
            String author,
            Long categoryId,
            MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();

        // because default search is filtering out archived books
        conditions.add("b.archived_at IS NULL");

        if (title != null && !title.isBlank()) {
            conditions.add("b.title ILIKE :titlePattern");

            params.addValue("titlePattern", "%" + title.trim() + "%");
        }

        if (author != null && !author.isBlank()) {
            conditions.add("b.author ILIKE :authorPattern");

            params.addValue("authorPattern", "%" + author.trim() + "%");
        }

        if (categoryId != null) {
            conditions.add("""
                    EXISTS (
                        SELECT 1
                        FROM book_categories bc
                        WHERE bc.book_id = b.id
                          AND bc.category_id = :categoryId
                    )
                    """);

            params.addValue("categoryId", categoryId);
        }

        return String.join("\n AND ", conditions);
    }

    private Book mapBook(ResultSet rs, int rowNum) throws SQLException {
        return new Book(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("isbn"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("cover_image_url"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class),
                rs.getObject("archived_at", java.time.OffsetDateTime.class)
        );
    }
}
