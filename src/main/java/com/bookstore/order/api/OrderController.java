
package com.bookstore.order.api;

import com.bookstore.common.api.PageResponse;
import com.bookstore.common.security.SecurityUtils;
import com.bookstore.idempotency.application.IdempotencyService;
import com.bookstore.idempotency.application.RequestHasher;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import com.bookstore.order.api.dto.OrderResponse;
import com.bookstore.order.application.OrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final RequestHasher requestHasher;

    public OrderController(
            OrderService orderService,
            IdempotencyService idempotencyService,
            RequestHasher requestHasher) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.requestHasher = requestHasher;
    }

    @PostMapping
    public ResponseEntity<?> submitOrder(@RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("action=submitOrder");
        Long userId = SecurityUtils.getCurrentUserId();

        String requestHash = requestHasher.hash("POST /api/orders", Map.of());

        return idempotencyService.execute(
                userId,
                IdempotencyOperation.CREATE_ORDER,
                idempotencyKey,
                requestHash,
                () -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(orderService.submitOrder(userId))
        );
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id,
                                         @RequestHeader("Idempotency-Key") String key) {
        Long userId = SecurityUtils.getCurrentUserId();
        return idempotencyService.execute(userId,
                IdempotencyOperation.CANCEL_ORDER, key,
                requestHasher.hash("POST /api/orders/" + id + "/cancel", Map.of()),
                () -> ResponseEntity.ok(orderService.cancelMyOrder(userId, id)));
    }

    @GetMapping
    public PageResponse<OrderResponse> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("action=getMyOrders, page={}, size={}", page, size);
        Long userId = SecurityUtils.getCurrentUserId();

        return orderService.getMyOrders(userId, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        log.info("action=getOrder, orderId={}", id);
        Long userId = SecurityUtils.getCurrentUserId();

        return orderService.getOrder(userId, id);
    }
}
