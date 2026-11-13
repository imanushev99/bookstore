package com.bookstore.auth.application.port;

import com.bookstore.user.domain.User;

public interface TokenProvider {
    String generateAccessToken(User user);
}
