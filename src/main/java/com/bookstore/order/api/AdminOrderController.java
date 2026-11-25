
package com.bookstore.order.api;

import com.bookstore.common.api.PageResponse;
import com.bookstore.common.security.SecurityUtils;
import com.bookstore.idempotency.application.IdempotencyService;
import com.bookstore.idempotency.application.RequestHasher;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import com.bookstore.order.api.dto.OrderResponse;
import com.bookstore.order.application.OrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderController.class);

    private final OrderService orderService;

    private final IdempotencyService idempotencyService;
    private final RequestHasher requestHasher;

    public AdminOrderController(OrderService orderService, IdempotencyService idempotencyService,
                                RequestHasher requestHasher) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.requestHasher = requestHasher;
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<?> shipOrder(@PathVariable Long id,
                                       @RequestHeader("Idempotency-Key") String key) {
        return idempotencyService.execute(SecurityUtils.getCurrentUserId(),
                IdempotencyOperation.SHIP_ORDER, key,
                requestHasher.hash("POST /api/admin/orders/" + id + "/ship", Map.of()),
                () -> ResponseEntity.ok(orderService.markAsShipped(id)));
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<?> deliverOrder(@PathVariable Long id,
                                          @RequestHeader("Idempotency-Key") String key) {
        return idempotencyService.execute(SecurityUtils.getCurrentUserId(),
                IdempotencyOperation.DELIVER_ORDER, key,
                requestHasher.hash("POST /api/admin/orders/" + id + "/deliver", Map.of()),
                () -> ResponseEntity.ok(orderService.markAsDelivered(id)));
    }

    @GetMapping
    public PageResponse<OrderResponse> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("action=getAllOrders, page={}, size={}", page, size);
        return orderService.getAllOrders(page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        log.info("action=getOrderAsAdmin, orderId={}", id);
        return orderService.getOrderAsAdmin(id);
    }
}
