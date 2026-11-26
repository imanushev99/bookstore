
package com.bookstore.order.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.cart.application.port.CartRepository;
import com.bookstore.cart.domain.Cart;
import com.bookstore.cart.domain.CartItem;
import com.bookstore.common.api.PageResponse;
import com.bookstore.common.application.AfterCommit;
import com.bookstore.inventory.application.InventoryAllocationService;
import com.bookstore.order.api.dto.OrderItemResponse;
import com.bookstore.order.api.dto.OrderResponse;
import com.bookstore.order.application.event.OrderSubmittedEvent;
import com.bookstore.order.application.exception.InvalidOrderStatusTransitionException;
import com.bookstore.order.application.port.OrderRepository;
import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;
import com.bookstore.outbox.application.port.OutboxRepository;
import com.bookstore.outbox.domain.OutboxEvent;
import com.bookstore.outbox.domain.OutboxEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final BookRepository bookRepository;
    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final InventoryAllocationService inventoryAllocationService;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            BookRepository bookRepository,
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper,
            InventoryAllocationService inventoryAllocationService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.bookRepository = bookRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.inventoryAllocationService = inventoryAllocationService;
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
        OutboxEvent savedEvent = outboxRepository.save(OutboxEvent.pending(
                        "ORDER",
                        savedOrder.getId(),
                        OutboxEventType.ORDER_SUBMITTED,
                        payload));

        // Clear items, but keep the cart.
        cartRepository.clearItems(cart.getId());

        AfterCommit.run(() -> log.info("Order submitted orderId={} eventId={} lineCount={}",
                savedOrder.getId(), savedEvent.getId(), orderItems.size()));

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

    @Transactional
    public OrderResponse markAsShipped(Long orderId) {
        validateOrderId(orderId);
        int updated = orderRepository.markShippedIfConfirmedAndAllocated(orderId);
        return completeTransition(orderId, updated, OrderStatus.SHIPPED,
                "Only CONFIRMED orders with fully allocated items can be shipped");
    }

    @Transactional
    public OrderResponse markAsDelivered(Long orderId) {
        validateOrderId(orderId);
        int updated = orderRepository.markDeliveredIfShipped(orderId);
        return completeTransition(orderId, updated, OrderStatus.DELIVERED,
                "Only SHIPPED orders can be delivered");
    }

    @Transactional
    public OrderResponse cancelMyOrder(Long userId, Long orderId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return cancelOrder(orderId, userId);
    }

    @Transactional
    public OrderResponse cancelOrderAsAdmin(Long orderId) {
        return cancelOrder(orderId, null);
    }

    // The HTTP idempotency claim, cancellation, stock refund and reassignment all
    // participate in the same transaction. Lock books before the order, matching
    // the allocation worker's book -> order lock order to avoid deadlocks.
    private OrderResponse cancelOrder(Long orderId, Long requestingUserId) {
        validateOrderId(orderId);

        // Order items are immutable regarding their book IDs. Read their IDs first,
        // but reread allocations after acquiring the book and order row locks.

        // Always acquire book locks before the order lock to maintain a consistent
        // locking order with InventoryAllocationService.
        //
        // Inventory allocation locks the book first, updates order item allocations,
        // and then updates the order status, acquiring an order row lock.
        //
        // Reversing this sequence could cause a deadlock between order cancellation
        // and concurrent inventory allocation or restocking.
        List<Long> bookIds = orderRepository.findItemsByOrderId(orderId).stream()
                .map(OrderItem::getBookId)
                .distinct()
                .sorted()
                .toList();

        for (Long bookId : bookIds) {
            // This lock includes archived books; orders can outlive catalog listings.
            bookRepository.findByIdForInventoryUpdate(bookId)
                    .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));
        }

        Order locked = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        ensureOwner(locked, requestingUserId);
        if (locked.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(locked);
        }
        ensureCancellable(locked);

        List<OrderItem> items = orderRepository.findItemsByOrderId(orderId);

        if (orderRepository.markCancelledIfNotShipped(orderId) != 1) {
            throw new InvalidOrderStatusTransitionException("Only unshipped orders can be cancelled");
        }

        Set<Long> refundedBookIds = new TreeSet<>();

        // Restore allocated inventory and reset outstanding quantities.
        for (OrderItem item : items) {
            if (item.getAllocatedQuantity() > 0) {
                bookRepository.increaseStock(item.getBookId(), item.getAllocatedQuantity());
                refundedBookIds.add(item.getBookId());
            }

            if (item.getAllocatedQuantity() > 0 || item.getBackorderedQuantity() > 0) {
                orderRepository.updateItemAllocation(item.getId(), 0, 0);
            }
        }

        // Redistribute restored inventory to outstanding orders.
        refundedBookIds.forEach(inventoryAllocationService::allocateOutstandingForBook);

        Order cancelled = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        AfterCommit.run(() -> log.info("Order cancelled orderId={}", orderId));

        return toResponse(cancelled);
    }

    private void ensureOwner(Order order, Long requestingUserId) {
        if (requestingUserId != null && !order.getUserId().equals(requestingUserId)) {
            // Do not disclose other customers' order IDs.
            throw new NoSuchElementException("Order not found");
        }
    }

    private void ensureCancellable(Order order) {
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStatusTransitionException("Only unshipped orders can be cancelled");
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Valid order ID is required");
        }
    }

    private OrderResponse completeTransition(Long orderId, int updated, OrderStatus target, String error) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        if (updated != 1) {
            throw new InvalidOrderStatusTransitionException(error);
        }
        AfterCommit.run(() -> log.info("Order status updated orderId={} status={}", orderId, target));
        return toResponse(order);
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
