
package com.bookstore.cart.api;

import com.bookstore.cart.api.dto.CartResponse;
import com.bookstore.cart.application.CartService;
import com.bookstore.common.security.SecurityUtils;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart() {
        Long userId = SecurityUtils.getCurrentUserId();

        return cartService.getCart(userId);
    }

    @PostMapping("/items/{bookId}")
    public CartResponse addBook(@PathVariable Long bookId) {
        Long userId = SecurityUtils.getCurrentUserId();

        return cartService.addBook(userId, bookId);
    }

    @DeleteMapping("/items/{bookId}")
    public CartResponse removeBook(@PathVariable Long bookId) {
        Long userId = SecurityUtils.getCurrentUserId();

        return cartService.removeBook(userId, bookId);
    }
}
