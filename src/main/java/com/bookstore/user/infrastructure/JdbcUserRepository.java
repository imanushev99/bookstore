package com.bookstore.user.infrastructure;

import com.bookstore.user.domain.User;
import com.bookstore.user.domain.UserRepository;
import com.bookstore.user.domain.UserRole;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<User> USER_ROW_MAPPER =
            (rs, rowNum) -> mapUser(rs);

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = """
                SELECT *
                FROM users
                WHERE id = :id
                """;

        List<User> users = jdbc.query(
                sql,
                Map.of("id", id),
                USER_ROW_MAPPER);

        return users.stream().findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = """
                SELECT *
                FROM users
                WHERE email = :email
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email);

        List<User> users = jdbc.query(
                sql,
                params,
                USER_ROW_MAPPER);

        return users.stream().findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM users
                    WHERE email = :email)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", email);

        Boolean exists = jdbc.queryForObject(
                sql,
                params,
                Boolean.class);

        // cannot do return exists; because exists could be null -> could throw NPE when unboxing null to boolean
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public User save(User user) {
        String sql = """
                    INSERT INTO users (email, password_hash, first_name, last_name, role)
                    VALUES (:email, :passwordHash, :firstName, :lastName, :role)
                    RETURNING id
                    """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("email", user.getEmail())
                .addValue("passwordHash", user.getPasswordHash())
                .addValue("firstName", user.getFirstName())
                .addValue("lastName", user.getLastName())
                .addValue("role", user.getRole().name());

        Long generatedId = jdbc.queryForObject(
                sql,
                params,
                Long.class
        );

        return findById(generatedId)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve created user"));
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                UserRole.valueOf(rs.getString("role")),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at",  java.time.OffsetDateTime.class)
        );
    }
}
