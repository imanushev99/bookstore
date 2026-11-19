
package com.bookstore.order.api;

import com.bookstore.common.api.PageResponse;
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

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
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
