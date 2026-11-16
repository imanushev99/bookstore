
package com.bookstore.order.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.cart.application.port.CartRepository;
import com.bookstore.cart.domain.Cart;
import com.bookstore.cart.domain.CartItem;
import com.bookstore.common.api.PageResponse;
import com.bookstore.order.api.dto.OrderItemResponse;
import com.bookstore.order.api.dto.OrderResponse;
import com.bookstore.order.application.event.OrderSubmittedEvent;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;
import com.bookstore.outbox.application.port.OutboxRepository;
import com.bookstore.outbox.domain.OutboxEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final BookRepository bookRepository;
    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            BookRepository bookRepository,
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.bookRepository = bookRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * BEGIN TRANSACTION
     *
     * 1. Lock cart
     * 2. Create order (PENDING)
     * 3. Create order items
     * 4. Insert ORDER_SUBMITTED into outbox_events
     * 5. Clear cart
     *
     * COMMIT
     * @param userId
     * @return
     */
    @Transactional
    public OrderResponse submitOrder(Long userId) {

        // Lock the user's cart to prevent concurrent modifications.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cannot submit an empty cart"));

        List<CartItem> cartItems =
                cartRepository.findItemsByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot submit an empty cart");
        }

        // Lock active books and capture current prices.
        Map<Long, Book> books = new HashMap<>();

        List<Long> bookIds = cartItems.stream()
                .map(CartItem::getBookId)
                .distinct()
                .sorted()
                .toList();

        for (Long bookId : bookIds) {
            Book book = bookRepository.findByIdForShare(bookId)
                    .orElseThrow(() -> new IllegalArgumentException("Book " + bookId + " is no longer available"));

            books.put(bookId, book);
        }

        // Convert cart items into order items.
        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> new OrderItem(
                        null,
                        null,
                        item.getBookId(),
                        item.getQuantity(),
                        0, //these will be set by the Kafka consumer
                        0,
                        books.get(item.getBookId()).getPrice()))
                .toList();

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create the order.
        Order order = new Order(
                null,
                userId,
                OrderStatus.PENDING,
                totalAmount,
                null,
                null);

        Order savedOrder = orderRepository.save(order);

        // Persist the items with their purchase prices.
        orderRepository.saveItems(savedOrder.getId(), orderItems);

        // Create event payload.
        OrderSubmittedEvent event = new OrderSubmittedEvent(savedOrder.getId());

        String payload = jsonMapper.writeValueAsString(event);

        // Persist event in the same transaction.
        // In this case aggregateId = payload.orderId
        // aggregate_id identifies the entity associated with the event.
        // We can use it for querying, grouping, and choosing the Kafka message key to preserve ordering for that entity.
        // payload contains the actual event data that the Kafka consumer needs to process.
        outboxRepository.save(OutboxEvent.pending(
                        "ORDER",
                        savedOrder.getId(),
                        "ORDER_SUBMITTED",
                        payload));

        // Clear items, but keep the cart.
        cartRepository.clearItems(cart.getId());

        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(existing -> existing.getUserId().equals(userId))
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderAsAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(Long userId, int page, int size) {
        validatePagination(page, size);

        long offset = (long) page * size;

        List<OrderResponse> orders = orderRepository
                .findByUserId(userId, size, offset)
                .stream()
                .map(this::toResponse)
                .toList();

        long total = orderRepository.countByUserId(userId);

        return new PageResponse<>(orders, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(int page, int size) {
        validatePagination(page, size);

        long offset = (long) page * size;

        List<OrderResponse> orders = orderRepository
                .findAll(size, offset)
                .stream()
                .map(this::toResponse)
                .toList();

        long total = orderRepository.countAll();

        return new PageResponse<>(orders, page, size, total);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = orderRepository
                .findItemsByOrderId(order.getId())
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getBookId(),
                item.getQuantity(),
                item.getAllocatedQuantity(),
                item.getBackorderedQuantity(),
                item.getUnitPrice()
        );
    }
}
