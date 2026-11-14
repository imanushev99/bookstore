package com.bookstore.category.infrastructure;

import com.bookstore.category.application.port.CategoryRepository;
import com.bookstore.category.domain.Category;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCategoryRepository implements CategoryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCategoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Category> findAll() {
        String sql = """
                SELECT id, name, description
                FROM categories
                ORDER BY name
                """;

        return jdbc.query(sql, this::mapCategory);
    }

    @Override
    public Optional<Category> findById(Long id) {
        String sql = """
                SELECT id, name, description
                FROM categories
                WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapCategory)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Category> findByIdForUpdate(Long id) {
        String sql = """
                SELECT id, name, description
                FROM categories
                WHERE id = :id
                FOR UPDATE NOWAIT
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapCategory)
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM categories
                    WHERE name = :name
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", name);

        Boolean exists = jdbc.queryForObject(sql, params, Boolean.class);

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Category save(Category category) {
        String sql = """
                INSERT INTO categories (name, description)
                VALUES (:name, :description)
                RETURNING id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", category.getName())
                .addValue("description", category.getDescription());

        Long id = jdbc.queryForObject(sql, params, Long.class);

        return new Category(id, category.getName(), category.getDescription());
    }

    @Override
    public void update(Category category) {
        String sql = """
                UPDATE categories
                SET name = :name,
                    description = :description
                WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", category.getId())
                .addValue("name", category.getName())
                .addValue("description", category.getDescription());

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected to update exactly one category");
        }
    }

    @Override
    public void deleteById(Long id) {
        String sql = """
                DELETE FROM categories
                WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        jdbc.update(sql, params);
    }

    private Category mapCategory(ResultSet rs, int rowNum) throws SQLException {
        return new Category(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"));
    }
}
