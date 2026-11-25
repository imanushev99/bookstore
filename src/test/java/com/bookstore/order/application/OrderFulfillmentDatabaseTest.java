package com.bookstore.order.application;

import com.bookstore.order.infrastructure.JdbcOrderRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class OrderFulfillmentDatabaseTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    static JdbcTemplate jdbc;
    static JdbcOrderRepository repository;
    static Long userId;
    static Long bookId;

    @BeforeAll
    static void setup() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        repository = new JdbcOrderRepository(new NamedParameterJdbcTemplate(ds));
        userId = jdbc.queryForObject("INSERT INTO users(email,password_hash,first_name,last_name) VALUES ('fulfillment@example.com','test','Test','User') RETURNING id", Long.class);
        bookId = jdbc.queryForObject("INSERT INTO books(title,author,price,isbn) VALUES ('Test','Author',10,'9780141182803') RETURNING id", Long.class);
    }

    private Long order(String status, Integer allocated, int backordered) {
        Long id = jdbc.queryForObject("INSERT INTO orders(user_id,status,total_amount) VALUES (?,?,20) RETURNING id", Long.class, userId, status);
        if (allocated != null) {
            jdbc.update("INSERT INTO order_items(order_id,book_id,quantity,allocated_quantity,backordered_quantity,unit_price) VALUES (?,?,2,?,?,10)", id, bookId, allocated, backordered);
        }
        return id;
    }

    @Test
    void rejectsInvalidShippingAndDelivery() {
        for (String status : new String[]{"PENDING","PROCESSING","BACKORDERED","SHIPPED","DELIVERED","CANCELLED"}) {
            assertEquals(0, repository.markShippedIfConfirmedAndAllocated(order(status, 2, 0)));
        }
        assertEquals(0, repository.markShippedIfConfirmedAndAllocated(order("CONFIRMED", 1, 1)));
        assertEquals(0, repository.markShippedIfConfirmedAndAllocated(order("CONFIRMED", null, 0)));
        assertEquals(0, repository.markDeliveredIfShipped(order("CONFIRMED", 2, 0)));
    }

    @Test
    void concurrentTransitionsHaveExactlyOneWinnerAndCannotBeReopened() throws Exception {
        Long id = order("CONFIRMED", 2, 0);
        assertOneWinner(() -> repository.markShippedIfConfirmedAndAllocated(id));
        repository.refreshAllocationStatus(id);
        assertEquals("SHIPPED", repository.findById(id).orElseThrow().getStatus().name());
        assertOneWinner(() -> repository.markDeliveredIfShipped(id));
        repository.refreshAllocationStatus(id);
        assertEquals("DELIVERED", repository.findById(id).orElseThrow().getStatus().name());
    }

    private void assertOneWinner(Callable<Integer> action) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> { barrier.await(10, TimeUnit.SECONDS); return action.call(); };
            var first = workers.submit(task);
            var second = workers.submit(task);
            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
        }
    }
}
