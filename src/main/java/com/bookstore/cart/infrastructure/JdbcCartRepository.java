package com.bookstore.cart.infrastructure;


import com.bookstore.cart.application.port.CartRepository;
import com.bookstore.cart.domain.Cart;
import com.bookstore.cart.domain.CartItem;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Repository
public class JdbcCartRepository implements CartRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCartRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        String sql = """
                SELECT *
                FROM carts
                WHERE user_id = :userId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);

        List<Cart> carts = jdbc.query(sql, params, this::mapCart);

        return carts.stream().findFirst();
    }

    @Override
    public Optional<Cart> findByUserIdForUpdate(Long userId) {
        String sql = """
                SELECT *
                FROM carts
                WHERE user_id = :userId
                FOR UPDATE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);

        List<Cart> carts = jdbc.query(sql, params, this::mapCart);

        return carts.stream().findFirst();
    }

    @Override
    public Cart getOrCreateForUpdate(Long userId) {
        String sql = """
                INSERT INTO carts (user_id)
                VALUES (:userId)
                ON CONFLICT (user_id) DO NOTHING
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);

        jdbc.update(sql, params);

        return findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve user cart"));
    }

    @Override
    public List<CartItem> findItemsByCartId(Long cartId) {
        String sql = """
                SELECT *
                FROM cart_items
                WHERE cart_id = :cartId
                ORDER BY id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId);

        return jdbc.query(sql, params, this::mapCartItem);
    }

    @Override
    public boolean addOne(Long cartId, Long bookId) {
        String sql = """
                INSERT INTO cart_items (
                    cart_id,
                    book_id,
                    quantity
                )
                SELECT :cartId, b.id, 1
                FROM books b
                WHERE b.id = :bookId
                  AND b.archived_at IS NULL
                ON CONFLICT (cart_id, book_id)
                DO UPDATE SET
                    quantity = cart_items.quantity + 1
                RETURNING id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId)
                .addValue("bookId", bookId);

        List<Long> insertedOrUpdated = jdbc.query(
                sql,
                params,
                (rs, rowNum) -> rs.getLong("id"));

        if (insertedOrUpdated.isEmpty()) {
            throw new NoSuchElementException("Book not found or archived");
        }

        touchCart(cartId);

        return true;
    }

    // A 2nd request will wait for the first to finish and then execute itself.
    // It will not get cancelled - there is no NOWAIT after FOR UPDATE.
    @Override
    public boolean removeOne(Long cartId, Long bookId) {
        String sql = """
                SELECT quantity
                FROM cart_items
                WHERE cart_id = :cartId
                  AND book_id = :bookId
                FOR UPDATE
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId)
                .addValue("bookId", bookId);

        List<Integer> quantities = jdbc.query(
                sql,
                params,
                (rs, rowNum) -> rs.getInt("quantity")
        );

        if (quantities.isEmpty()) {
            return false;
        }

        int quantity = quantities.getFirst();

        if (quantity == 1) {
            String deleteSql = """
                    DELETE FROM cart_items
                    WHERE cart_id = :cartId
                      AND book_id = :bookId
                    """;

            jdbc.update(deleteSql, params);
        } else {
            String updateSql = """
                    UPDATE cart_items
                    SET quantity = quantity - 1
                    WHERE cart_id = :cartId
                      AND book_id = :bookId
                    """;

            jdbc.update(updateSql, params);
        }

        touchCart(cartId);

        return true;
    }

    @Override
    public void clearItems(Long cartId) {
        String sql = """
                DELETE FROM cart_items
                WHERE cart_id = :cartId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cartId", cartId);

        int deleted = jdbc.update(sql, params);

        if (deleted > 0) {
            touchCart(cartId);
        }
    }

    private void touchCart(Long cartId) {
        String sql = """
                UPDATE carts
                SET updated_at = CURRENT_TIMESTAMP
                WHERE id = :cartId
                """;

        jdbc.update(sql,
                new MapSqlParameterSource()
                        .addValue("cartId", cartId));
    }

    private Cart mapCart(ResultSet rs, int rowNum) throws SQLException {
        return new Cart(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private CartItem mapCartItem(ResultSet rs, int rowNum) throws SQLException {
        return new CartItem(
                rs.getLong("id"),
                rs.getLong("cart_id"),
                rs.getLong("book_id"),
                rs.getInt("quantity"));
    }
}
