package com.bookstore.user.infrastructure;

import com.bookstore.user.domain.User;
import com.bookstore.user.application.port.UserRepository;
import com.bookstore.user.domain.UserRole;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private final NamedParameterJdbcTemplate jdbc;

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

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapUser)
                .stream()
                .findFirst();
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

        return jdbc.query(sql, params, this::mapUser)
                .stream().findFirst();
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

    // Use FOR UPDATE NOWAIT, so overlapping requests do not silently overwrite one another.
    // This prevents simultaneous modification, but it does not prevent sequential updates made after the first transaction commits.
    // => For protection against stale profile data, we could additionally introduce optimistic versioning later.
    @Override
    public Optional<User> findByIdForUpdate(Long id) {
        String sql = """
            SELECT *
            FROM users
            WHERE id = :id
            FOR UPDATE NOWAIT
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        return jdbc.query(sql, params, this::mapUser)
                .stream()
                .findFirst();
    }

    @Override
    public void updateProfile(Long userId, String firstName, String lastName) {
        String sql = """
            UPDATE users
            SET first_name = :firstName,
                last_name = :lastName,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :userId
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("firstName", firstName)
                .addValue("lastName", lastName);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected to update exactly one user");
        }
    }

    @Override
    public void updatePassword(Long userId, String passwordHash) {
        String sql = """
            UPDATE users
            SET password_hash = :passwordHash,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :userId
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("passwordHash", passwordHash);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Expected to update exactly one user");
        }
    }

    private User mapUser(ResultSet rs, int rowNum) throws SQLException {
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
