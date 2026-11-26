package com.bookstore.order.application;

import com.bookstore.book.infrastructure.JdbcBookRepository;
import com.bookstore.inventory.application.InventoryAllocationService;
import com.bookstore.order.application.exception.InvalidOrderStatusTransitionException;
import com.bookstore.order.infrastructure.JdbcOrderRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class OrderCancellationDatabaseTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final AtomicLong sequence = new AtomicLong();
    static JdbcTemplate jdbc;
    static OrderService service;
    static TransactionTemplate tx;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        var namedJdbc = new NamedParameterJdbcTemplate(ds);
        var orders = new JdbcOrderRepository(namedJdbc);
        var books = new JdbcBookRepository(namedJdbc);
        var allocator = new InventoryAllocationService(books, orders);
        service = new OrderService(orders, null, books, null, null, allocator);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    private long user() {
        long n = sequence.incrementAndGet();
        return jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,first_name,last_name)
                VALUES (?, 'hash', 'Test', 'User') RETURNING id
                """, Long.class, "cancel-test-" + n + "@example.org");
    }

    private long book(int stock, boolean archived) {
        long n = sequence.incrementAndGet();
        long id = jdbc.queryForObject("""
                INSERT INTO books(title, author, price, isbn, stock)
                VALUES ('Title', 'Author', 10, ?, ?) RETURNING id
                """, Long.class, String.format("%013d", n), stock);
        if (archived) {
            jdbc.update("UPDATE books SET archived_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        }
        return id;
    }

    private long order(long userId, long bookId, String status, int quantity, int allocated, int backordered) {
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders(user_id, status, total_amount)
                VALUES (?, ?, 10) RETURNING id
                """, Long.class, userId, status);
        jdbc.update("""
                INSERT INTO order_items(order_id,book_id,quantity,allocated_quantity,backordered_quantity,unit_price)
                VALUES (?,?,?,?,?,10)
                """, orderId, bookId, quantity, allocated, backordered);
        return orderId;
    }

    private int stock(long bookId) {
        return jdbc.queryForObject("SELECT stock FROM books WHERE id = ?", Integer.class, bookId);
    }

    private int allocated(long orderId) {
        return jdbc.queryForObject("SELECT allocated_quantity FROM order_items WHERE order_id = ?", Integer.class, orderId);
    }

    private int backordered(long orderId) {
        return jdbc.queryForObject("SELECT backordered_quantity FROM order_items WHERE order_id = ?", Integer.class, orderId);
    }

    private String status(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    @Test
    void cancellingPartialAllocationReassignsUnitsToOldestWaitingOrder() {
        long userId = user();
        long bookId = book(0, false);
        long waiting = order(userId, bookId, "BACKORDERED", 2, 0, 2);
        long cancelled = order(userId, bookId, "BACKORDERED", 3, 2, 1);

        tx.executeWithoutResult(t -> service.cancelMyOrder(userId, cancelled));

        assertEquals("CANCELLED", status(cancelled));
        assertEquals(0, allocated(cancelled));
        assertEquals(0, backordered(cancelled));
        assertEquals("CONFIRMED", status(waiting));
        assertEquals(2, allocated(waiting));
        assertEquals(0, backordered(waiting));
        assertEquals(0, stock(bookId));

        // A retry using a new idempotency key must not release inventory twice.
        tx.executeWithoutResult(t -> service.cancelMyOrder(userId, cancelled));
        assertEquals(0, stock(bookId));
        assertEquals(2, allocated(waiting));
    }

    @Test
    void cancellingAnUnallocatedPendingOrderDoesNotChangeStock() {
        long userId = user();
        long bookId = book(5, false);
        long orderId = order(userId, bookId, "PENDING", 3, 0, 0);

        tx.executeWithoutResult(t -> service.cancelMyOrder(userId, orderId));

        assertEquals("CANCELLED", status(orderId));
        assertEquals(5, stock(bookId));
        assertEquals(0, allocated(orderId));
    }

    @Test
    void cancellingAnArchivedBooksOrderRestoresPreviouslyReservedStock() {
        long userId = user();
        long bookId = book(0, true);
        long orderId = order(userId, bookId, "CONFIRMED", 2, 2, 0);

        tx.executeWithoutResult(t -> service.cancelOrderAsAdmin(orderId));

        assertEquals("CANCELLED", status(orderId));
        assertEquals(2, stock(bookId));
        assertEquals(0, allocated(orderId));
    }

    @Test
    void refusesShippedOrdersAndForeignCustomers() {
        long ownerId = user();
        long bookId = book(0, false);
        long orderId = order(ownerId, bookId, "SHIPPED", 2, 2, 0);

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> tx.executeWithoutResult(t -> service.cancelOrderAsAdmin(orderId)));
        assertThrows(java.util.NoSuchElementException.class,
                () -> tx.executeWithoutResult(t -> service.cancelMyOrder(user(), orderId)));
        assertEquals("SHIPPED", status(orderId));
        assertEquals(2, allocated(orderId));
        assertEquals(0, stock(bookId));
    }

    @Test
    void failureToRestoreInventoryRollsBackCancellation() {
        long userId = user();
        long bookId = book(Integer.MAX_VALUE, false);
        long orderId = order(userId, bookId, "CONFIRMED", 1, 1, 0);

        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(t -> service.cancelMyOrder(userId, orderId)));
        assertEquals("CONFIRMED", status(orderId));
        assertEquals(1, allocated(orderId));
        assertEquals(Integer.MAX_VALUE, stock(bookId));
    }

    @Test
    void cancellationAndShippingCannotBothWin() throws Exception {
        long userId = user();
        long bookId = book(0, false);
        long orderId = order(userId, bookId, "CONFIRMED", 2, 2, 0);

        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var cancel = workers.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.executeWithoutResult(t -> service.cancelMyOrder(userId, orderId));
                    return true;
                } catch (InvalidOrderStatusTransitionException expected) {
                    return false;
                }
            });
            var ship = workers.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.executeWithoutResult(t -> service.markAsShipped(orderId));
                    return true;
                } catch (InvalidOrderStatusTransitionException expected) {
                    return false;
                }
            });

            boolean cancelled = cancel.get(20, TimeUnit.SECONDS);
            boolean shipped = ship.get(20, TimeUnit.SECONDS);
            assertNotEquals(cancelled, shipped);
            assertEquals(cancelled ? "CANCELLED" : "SHIPPED", status(orderId));
            assertEquals(cancelled ? 2 : 0, stock(bookId));
            assertEquals(cancelled ? 0 : 2, allocated(orderId));
        }
    }
}
