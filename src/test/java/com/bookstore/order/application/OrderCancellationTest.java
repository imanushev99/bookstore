package com.bookstore.order.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.inventory.application.InventoryAllocationService;
import com.bookstore.order.application.exception.InvalidOrderStatusTransitionException;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCancellationTest {
    private static final long ORDER_ID = 11L;
    private static final long OWNER_ID = 7L;

    private OrderRepository orders;
    private BookRepository books;
    private InventoryAllocationService allocation;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        books = mock(BookRepository.class);
        allocation = mock(InventoryAllocationService.class);
        service = new OrderService(orders, null, books, null, null, allocation);
    }

    private Order order(OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Order(ORDER_ID, OWNER_ID, status, BigDecimal.TEN, now, now);
    }

    private void setupTransition(OrderStatus status, List<OrderItem> items) {
        when(orders.findById(ORDER_ID))
                .thenReturn(Optional.of(order(status)), Optional.of(order(OrderStatus.CANCELLED)));
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order(status)));
        when(orders.findItemsByOrderId(ORDER_ID)).thenReturn(items);
        when(orders.markCancelledIfNotShipped(ORDER_ID)).thenReturn(1);
        for (OrderItem item : items) {
            when(books.findByIdForInventoryUpdate(item.getBookId())).thenReturn(Optional.of(mock(Book.class)));
        }
    }

    @Test
    void refundsAllocatedOnlyAndReallocatesInSortedBookOrder() {
        // Input items intentionally have reverse book order.
        OrderItem first = new OrderItem(101L, ORDER_ID, 9L, 5, 2, 3, BigDecimal.TEN);
        OrderItem second = new OrderItem(102L, ORDER_ID, 3L, 4, 0, 4, BigDecimal.TEN);
        setupTransition(OrderStatus.BACKORDERED, List.of(first, second));

        assertEquals(OrderStatus.CANCELLED, service.cancelMyOrder(OWNER_ID, ORDER_ID).getStatus());

        var order = inOrder(books, orders, allocation);
        order.verify(books).findByIdForInventoryUpdate(3L);
        order.verify(books).findByIdForInventoryUpdate(9L);
        order.verify(orders).findByIdForUpdate(ORDER_ID);
        order.verify(orders).markCancelledIfNotShipped(ORDER_ID);
        order.verify(books).increaseStock(9L, 2);
        order.verify(allocation).allocateOutstandingForBook(9L);
        verify(books, never()).increaseStock(eq(3L), anyInt());
        verify(allocation, never()).allocateOutstandingForBook(3L);
        verify(orders).updateItemAllocation(101L, 0, 0);
        verify(orders).updateItemAllocation(102L, 0, 0);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "PROCESSING", "CONFIRMED", "BACKORDERED"})
    void allUnshippedStatusesCanBeCancelled(OrderStatus status) {
        setupTransition(status, List.of(new OrderItem(101L, ORDER_ID, 9L, 1, 0, 0, BigDecimal.ONE)));
        assertEquals(OrderStatus.CANCELLED, service.cancelOrderAsAdmin(ORDER_ID).getStatus());
        verify(orders).markCancelledIfNotShipped(ORDER_ID);
        verify(books, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    void cancellingAgainDoesNotRestoreStockTwice() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.CANCELLED)));
        when(orders.findItemsByOrderId(ORDER_ID)).thenReturn(List.of());

        assertEquals(OrderStatus.CANCELLED, service.cancelMyOrder(OWNER_ID, ORDER_ID).getStatus());
        verify(orders, never()).markCancelledIfNotShipped(anyLong());
        verifyNoInteractions(books, allocation);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED"})
    void cannotCancelShippedOrDeliveredOrders(OrderStatus status) {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order(status)));
        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> service.cancelOrderAsAdmin(ORDER_ID));
        verifyNoInteractions(books, allocation);
    }

    @Test
    void cannotCancelAnotherCustomersOrder() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        assertThrows(NoSuchElementException.class, () -> service.cancelMyOrder(999L, ORDER_ID));
        verifyNoInteractions(books, allocation);
    }

    @Test
    void rechecksStatusAfterLockToPreventRaceWithShipping() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.SHIPPED)));
        when(orders.findItemsByOrderId(ORDER_ID)).thenReturn(List.of(
                new OrderItem(101L, ORDER_ID, 9L, 2, 2, 0, BigDecimal.ONE)));
        when(books.findByIdForInventoryUpdate(9L)).thenReturn(Optional.of(mock(Book.class)));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> service.cancelMyOrder(OWNER_ID, ORDER_ID));
        verify(orders, never()).markCancelledIfNotShipped(anyLong());
        verify(books, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    void missingOrderAndInvalidIdDoNotModifyInventory() {
        assertThrows(IllegalArgumentException.class, () -> service.cancelOrderAsAdmin(0L));
        assertThrows(NoSuchElementException.class, () -> service.cancelMyOrder(OWNER_ID, ORDER_ID));
        verifyNoInteractions(books, allocation);
    }
}
