
package com.bookstore.cart.application;

import com.bookstore.book.application.port.BookRepository;
import com.bookstore.book.domain.Book;
import com.bookstore.cart.api.dto.CartItemResponse;
import com.bookstore.cart.api.dto.CartResponse;
import com.bookstore.cart.application.port.CartRepository;
import com.bookstore.cart.domain.Cart;
import com.bookstore.cart.domain.CartItem;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final BookRepository bookRepository;

    public CartService(CartRepository cartRepository, BookRepository bookRepository) {
        this.cartRepository = cartRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .map(this::toResponse)
                .orElseGet(() -> new CartResponse(null, List.of(), null, null));
    }

    @Transactional
    public CartResponse addBook(Long userId, Long bookId) {
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Book ID must be positive");
        }

        // Verify that the book exists and is not archived.
        bookRepository.findById(bookId)
                .orElseThrow(() -> new NoSuchElementException("Book not found or archived"));

        // Create the cart if necessary and lock its row.
        Cart cart = cartRepository.getOrCreateForUpdate(userId);

        // Atomically increase quantity by one.
        cartRepository.addOne(cart.getId(), bookId);

        return getUpdatedCart(cart.getId(), userId);
    }

    @Transactional
    public CartResponse removeBook(Long userId, Long bookId) {
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Book ID must be positive");
        }

        // No cart means there is nothing to remove.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElse(null);

        if (cart == null) {
            return new CartResponse(null, List.of(), null, null);
        }

        // Decrease quantity by one, deleting the item at zero.
        cartRepository.removeOne(cart.getId(), bookId);

        return getUpdatedCart(cart.getId(), userId);
    }

    private CartResponse getUpdatedCart(Long cartId, Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Cart not found after modification"));

        if (!cart.getId().equals(cartId)) {
            throw new IllegalStateException("Unexpected cart ID");
        }

        return toResponse(cart);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> items = cartRepository.findItemsByCartId(cart.getId());

        List<CartItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .flatMap(Optional::stream)
                .toList();

        return new CartResponse(
                cart.getId(),
                itemResponses,
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }

    // findById filters unarchived books.
    // If an admin archives a book that's already in someone's cart findById will throw an exception.
    // => Solution: If a book is not found, look for it in the archived - if found, remove it from cart. (lazy cleanup)
    // Opposite way: When a book is archived it is removed form all carts looks too complicated - you should go through all carts.

    private Optional<CartItemResponse> toItemResponse(CartItem item) {
        Book book;

        try {
            book = bookRepository.findById(item.getBookId())
                    .orElseThrow(() -> new NoSuchElementException("Book not found: " + item.getBookId()));
        } catch (NoSuchElementException exception) {
            if (bookRepository.findArchivedById(item.getBookId()).isPresent()) {

                cartRepository.deleteItem(item.getCartId(), item.getBookId());

                return Optional.empty();
            }

            throw exception;
        }

        return Optional.of(new CartItemResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPrice(),
                item.getQuantity(),
                book.getCoverImageUrl()
        ));
    }

}
