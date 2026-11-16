package com.bookstore.cart.application.port;

import com.bookstore.cart.domain.Cart;
import com.bookstore.cart.domain.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartRepository {
    Optional<Cart> findByUserId(Long userId);

    Optional<Cart> findByUserIdForUpdate(Long userId);

    Cart getOrCreateForUpdate(Long userId);

    List<CartItem> findItemsByCartId(Long cartId);

    boolean addOne(Long cartId, Long bookId);

    boolean removeOne(Long cartId, Long bookId);

    void clearItems(Long cartId);
}
