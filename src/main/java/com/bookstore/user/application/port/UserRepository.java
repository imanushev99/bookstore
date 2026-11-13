package com.bookstore.user.application.port;

import com.bookstore.user.domain.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);

    // Acquires an exclusive row lock for the transaction.
    // Must be called inside an active transaction.
    Optional<User> findByIdForUpdate(Long id);
    void updateProfile(Long userId, String firstName, String lastName);
    void updatePassword(Long userId, String passwordHash);
}
