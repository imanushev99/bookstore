
package com.bookstore.order.application.port;

import com.bookstore.order.domain.Order;
import com.bookstore.order.domain.OrderItem;
import com.bookstore.order.domain.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    void saveItems(Long orderId, List<OrderItem> items);

    List<Order> findAll(int limit, long offset);

    long countAll();

    Optional<Order> findById(Long id);

    Optional<Order> findByIdForUpdate(Long id);

    List<Order> findByUserId(Long userId, int limit, long offset);

    long countByUserId(Long userId);

    List<OrderItem> findItemsByOrderId(Long orderId);

    int markProcessingIfPending(Long orderId);

    //return both pending and backordered, oldest first
    List<OrderItem> findOutstandingItemsByBookId(Long bookId);

    void updateItemAllocation(
            Long orderItemId,
            int allocatedQuantity,
            int backorderedQuantity
    );

    void refreshAllocationStatus(Long orderId);
}
