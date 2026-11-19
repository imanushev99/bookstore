package com.bookstore.auth.api;

import com.bookstore.auth.api.dto.AuthResponse;
import com.bookstore.auth.api.dto.LoginRequest;
import com.bookstore.auth.api.dto.RegisterRequest;
import com.bookstore.auth.application.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("action=register");
        AuthResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("action=login");
        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint at this point is meaningless, because it won't invalidate the JWT.
     * Logout could be handled client-side by discarding the access token.
     * For now, we rely on the expiration time.
     * In the future: refresh token can be introduced.
     * Or: store 'invalidated' JWTs in Redis and check the token on each request
     */
}
