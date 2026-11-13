package com.bookstore.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication instanceof JwtAuthenticationToken)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user found");
        }

        JwtAuthenticationToken jwtAuthentication = (JwtAuthenticationToken) authentication;

        return Long.valueOf(jwtAuthentication.getToken().getSubject());
    }
}
