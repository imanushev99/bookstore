
package com.bookstore.order.api;

import com.bookstore.common.api.PageResponse;
import com.bookstore.common.security.SecurityUtils;
import com.bookstore.idempotency.application.IdempotencyService;
import com.bookstore.idempotency.application.RequestHasher;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import com.bookstore.order.api.dto.OrderResponse;
import com.bookstore.order.application.OrderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

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

    @GetMapping
    public PageResponse<OrderResponse> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();

        return orderService.getMyOrders(userId, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();

        return orderService.getOrder(userId, id);
    }
}
