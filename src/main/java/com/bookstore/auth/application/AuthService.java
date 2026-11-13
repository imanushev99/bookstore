package com.bookstore.auth.application;

import com.bookstore.auth.api.dto.AuthResponse;
import com.bookstore.auth.api.dto.LoginRequest;
import com.bookstore.auth.api.dto.RegisterRequest;
import com.bookstore.auth.api.dto.TokenType;
import com.bookstore.auth.application.exception.EmailAlreadyExistsException;
import com.bookstore.auth.application.port.TokenProvider;
import com.bookstore.user.application.port.UserRepository;
import com.bookstore.user.domain.User;
import com.bookstore.user.domain.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;

@Service
public class AuthService {
    public static final String $_APP_JWT_EXPIRATION_MINUTES_OR_15 = "${app.jwt.expiration-minutes:15}";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final Duration tokenLifetime;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider,
            @Value($_APP_JWT_EXPIRATION_MINUTES_OR_15) long expirationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.tokenLifetime = Duration.ofMinutes(expirationMinutes);
    }

    // Pessimistic locking with "FOR UPDATE" will not work because if email is not present, there is no row to lock
    // SERIALIZABLE isolation level offers more complexity and will still return an error to the user
    // - retry mechanism with same email is pointless
    // "ON CONFLICT (email) DO NOTHING" will still return an error to the user
    // DB unique constraint on emails guarantees there are no email duplicates
    // @Transactional helps only in case there is a failure after the user is saved (JWT fails for example => savedUser is roll backed);
    // (there are no multiple writes that require to be executed in the same transaction)
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(
                null,
                email,
                passwordHash,
                request.getFirstName(),
                request.getLastName(),
                UserRole.USER,
                null,
                null);

        User savedUser;

        try {
            savedUser = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        String accessToken = tokenProvider.generateAccessToken(savedUser);

        return new AuthResponse(
                accessToken,
                TokenType.BEARER,
                tokenLifetime.toSeconds()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = tokenProvider.generateAccessToken(user);

        return new AuthResponse(
                accessToken,
                TokenType.BEARER,
                tokenLifetime.toSeconds()
        );
    }
}
