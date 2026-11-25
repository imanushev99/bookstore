
package com.bookstore.order.infrastructure;

import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Order save(Order order) {
        String sql = """
                INSERT INTO orders (
                    user_id,
                    status,
                    total_amount)
                VALUES (
                    :userId,
                    :status,
                    :totalAmount)
                RETURNING *
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", order.getUserId())
                .addValue("status", order.getStatus().name())
                .addValue("totalAmount", order.getTotalAmount());

        return jdbc.queryForObject(sql, params, this::mapOrder);
    }

    @Override
    public void saveItems(Long orderId, List<OrderItem> items) {
        String sql = """
                INSERT INTO order_items (
                    order_id,
                    book_id,
                    quantity,
                    allocated_quantity,
                    backordered_quantity,
                    unit_price)
                VALUES (
                    :orderId,
                    :bookId,
                    :quantity,
                    :allocatedQuantity,
                    :backorderedQuantity,
                    :unitPrice)
                """;

        SqlParameterSource[] batch = items.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("orderId", orderId)
                        .addValue("bookId", item.getBookId())
                        .addValue("quantity", item.getQuantity())
                        .addValue("allocatedQuantity", item.getAllocatedQuantity())
                        .addValue("backorderedQuantity", item.getBackorderedQuantity())
                        .addValue("unitPrice", item.getUnitPrice()))
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate(sql, batch);
    }

    @Override
    public List<Order> findAll(int limit, long offset) {
        String sql = """
            SELECT *
            FROM orders
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            OFFSET :offset
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(sql, params, this::mapOrder);
    }

    @Override
    public long countAll() {
        String sql = """
            SELECT COUNT(*)
            FROM orders
            """;

        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);

        return count == null ? 0L : count;
    }

    @Override
    public Optional<Order> findById(Long id) {
        String sql = """
                SELECT *
                FROM orders
                WHERE id = :id
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("id", id);

        return jdbc.query(sql, params, this::mapOrder)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long id) {
        String sql = """
                SELECT *
                FROM orders
                WHERE id = :id
                FOR UPDATE NOWAIT
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("id", id);

        return jdbc.query(sql, params, this::mapOrder)
                .stream()
                .findFirst();
    }

    @Override
    public List<Order> findByUserId(Long userId, int limit, long offset) {
        String sql = """
                SELECT *
                FROM orders
                WHERE user_id = :userId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                OFFSET :offset
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return jdbc.query(sql, params, this::mapOrder);
    }

    @Override
    public long countByUserId(Long userId) {
        String sql = """
                SELECT COUNT(*)
                FROM orders
                WHERE user_id = :userId
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("userId", userId);

        Long count = jdbc.queryForObject(sql, params, Long.class);

        return count == null ? 0L : count;
    }

    @Override
    public List<OrderItem> findItemsByOrderId(Long orderId) {
        String sql = """
                SELECT *
                FROM order_items
                WHERE order_id = :orderId
                ORDER BY id
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("orderId", orderId);

        return jdbc.query(sql, params, this::mapOrderItem);
    }

    @Override
    public int markProcessingIfPending(Long orderId) {
        String sql = """
                UPDATE orders
                SET status = 'PROCESSING',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :orderId
                AND status = 'PENDING'
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderId", orderId);

        return jdbc.update(sql, params);
    }

    @Override
    public int markShippedIfConfirmedAndAllocated(Long orderId) {
        String sql = """
                UPDATE orders o
                SET status = 'SHIPPED', updated_at = CURRENT_TIMESTAMP
                WHERE o.id = :orderId AND o.status = 'CONFIRMED'
                  AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id)
                  AND NOT EXISTS (
                      SELECT 1 FROM order_items oi WHERE oi.order_id = o.id
                      AND (oi.allocated_quantity <> oi.quantity OR oi.backordered_quantity <> 0)
                  )
                """;
        return jdbc.update(sql, new MapSqlParameterSource("orderId", orderId));
    }

    @Override
    public int markDeliveredIfShipped(Long orderId) {
        String sql = """
                UPDATE orders
                SET status = 'DELIVERED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :orderId AND status = 'SHIPPED'
                """;
        return jdbc.update(sql, new MapSqlParameterSource("orderId", orderId));
    }

    // includes:
    //   Orders awaiting initial processing (PENDING).
    //   Orders currently being processed (PROCESSING).
    //   Orders still waiting for inventory (BACKORDERED)
    // excludes:
    //   completed or cancelled orders
    // Orders are handled chronologically, not based on which Kafka event is consumed first
    @Override
    public List<OrderItem> findOutstandingItemsByBookId(Long bookId) {
        String sql = """
            SELECT oi.*
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.book_id = :bookId
              AND oi.allocated_quantity < oi.quantity
              AND o.status IN (
                  'PENDING',
                  'PROCESSING',
                  'BACKORDERED')
            ORDER BY o.created_at ASC,
                     o.id ASC,
                     oi.id ASC
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("bookId", bookId);

        return jdbc.query(sql, params, this::mapOrderItem);
    }



    @Override
    public void updateItemAllocation(Long orderItemId, int allocatedQuantity, int backorderedQuantity) {
        if (allocatedQuantity < 0 || backorderedQuantity < 0) {
            throw new IllegalArgumentException("Allocation quantities cannot be negative");
        }

        String sql = """
            UPDATE order_items
            SET allocated_quantity = :allocatedQuantity,
                backordered_quantity = :backorderedQuantity
            WHERE id = :orderItemId
              AND :allocatedQuantity + :backorderedQuantity <= quantity
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orderItemId", orderItemId)
                .addValue("allocatedQuantity", allocatedQuantity)
                .addValue("backorderedQuantity", backorderedQuantity);

        int updatedRows = jdbc.update(sql, params);

        if (updatedRows != 1) {
            throw new IllegalStateException("Order item not found or invalid allocation: " + orderItemId);
        }
    }

    // recalculates the order status based on its items
    @Override
    public void refreshAllocationStatus(Long orderId) {
        String sql = """
            UPDATE orders o
            SET status = CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM order_items oi
                        WHERE oi.order_id = o.id
                          AND (oi.allocated_quantity + oi.backordered_quantity) < oi.quantity
                    )
                    THEN CASE
                            WHEN o.status = 'PROCESSING' THEN 'PROCESSING'
                            ELSE 'PENDING'
                        END

                    WHEN EXISTS (
                        SELECT 1
                        FROM order_items oi
                        WHERE oi.order_id = o.id AND oi.backordered_quantity > 0
                    )
                    THEN 'BACKORDERED'

                    ELSE 'CONFIRMED'
                END,
                updated_at = CURRENT_TIMESTAMP
            WHERE o.id = :orderId
              AND o.status IN (
                  'PENDING',
                  'PROCESSING',
                  'BACKORDERED'
              )
              AND EXISTS (
                  SELECT 1
                  FROM order_items oi
                  WHERE oi.order_id = o.id
              )
            """;

        MapSqlParameterSource params =
                new MapSqlParameterSource("orderId", orderId);

        jdbc.update(sql, params);
    }


    private Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
        return new Order(
                rs.getLong("id"),
                rs.getLong("user_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("total_amount"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private OrderItem mapOrderItem(ResultSet rs, int rowNum) throws SQLException {
        return new OrderItem(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getLong("book_id"),
                rs.getInt("quantity"),
                rs.getInt("allocated_quantity"),
                rs.getInt("backordered_quantity"),
                rs.getBigDecimal("unit_price")
        );
    }
}
