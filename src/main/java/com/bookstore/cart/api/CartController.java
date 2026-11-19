
package com.bookstore.cart.api;

import com.bookstore.cart.api.dto.CartResponse;
import com.bookstore.cart.application.CartService;
import com.bookstore.common.security.SecurityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart() {
        log.info("action=getCart");
        Long userId = SecurityUtils.getCurrentUserId();

        return cartService.getCart(userId);
    }

    @PostMapping("/items/{bookId}")
    public CartResponse addBook(@PathVariable Long bookId) {
        log.info("action=addBookToCart, bookId={}", bookId);
        Long userId = SecurityUtils.getCurrentUserId();

        return cartService.addBook(userId, bookId);
    }

    @DeleteMapping("/items/{bookId}")
    public CartResponse removeBook(@PathVariable Long bookId) {
        log.info("action=removeBookFromCart, bookId={}", bookId);
        Long userId = SecurityUtils.getCurrentUserId();

        return cartService.removeBook(userId, bookId);
    }
}
