package com.bookstore.order.application;

import com.bookstore.order.application.exception.InvalidOrderStatusTransitionException;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
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

class OrderFulfillmentTest {
    private OrderRepository repository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        service = new OrderService(repository, null, null, null, null);
    }

    private void existing(OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        when(repository.findById(1L)).thenReturn(Optional.of(
                new Order(1L, 2L, status, BigDecimal.TEN, now, now)));
        when(repository.findItemsByOrderId(1L)).thenReturn(List.of());
    }

    @Test
    void shipsSuccessfully() {
        existing(OrderStatus.SHIPPED);
        when(repository.markShippedIfConfirmedAndAllocated(1L)).thenReturn(1);
        assertEquals(OrderStatus.SHIPPED, service.markAsShipped(1L).getStatus());
        verify(repository).markShippedIfConfirmedAndAllocated(1L);
    }

    @Test
    void deliversSuccessfully() {
        existing(OrderStatus.DELIVERED);
        when(repository.markDeliveredIfShipped(1L)).thenReturn(1);
        assertEquals(OrderStatus.DELIVERED, service.markAsDelivered(1L).getStatus());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void rejectsShippingWhenConditionalUpdateFails(OrderStatus status) {
        existing(status);
        assertThrows(InvalidOrderStatusTransitionException.class, () -> service.markAsShipped(1L));
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void rejectsDeliveryWhenConditionalUpdateFails(OrderStatus status) {
        existing(status);
        assertThrows(InvalidOrderStatusTransitionException.class, () -> service.markAsDelivered(1L));
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.markAsShipped(1L));
        assertThrows(NoSuchElementException.class, () -> service.markAsDelivered(1L));
    }

    @Test
    void invalidIdDoesNotAccessDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.markAsShipped(0L));
        assertThrows(IllegalArgumentException.class, () -> service.markAsDelivered(-1L));
        verifyNoInteractions(repository);
    }
}
