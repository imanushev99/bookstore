package com.bookstore.user.application;

import com.bookstore.user.api.dto.ChangePasswordRequest;
import com.bookstore.user.api.dto.UpdateProfileRequest;
import com.bookstore.user.api.dto.UserResponse;
import com.bookstore.user.application.port.UserRepository;
import com.bookstore.user.domain.User;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class UserService {

    public static final String USER_NOT_FOUND = "User not found";
    public static final String CURRENT_PASSWORD_IS_INCORRECT = "Current password is incorrect";
    public static final String NEW_PASSWORD_MUST_BE_DIFFERENT_FROM_CURRENT_PASSWORD = "New password must be different from current password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = findUser(userId);
        return toResponse(user);
    }

    // use pessimistic locking to prevent overlapping updates
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        findUserForUpdate(userId);

        userRepository.updateProfile(userId, request.getFirstName().trim(), request.getLastName().trim());

        return toResponse(findUser(userId));
    }

    // use pessimistic locking to prevent overlapping updates
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserForUpdate(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException(CURRENT_PASSWORD_IS_INCORRECT);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException(NEW_PASSWORD_MUST_BE_DIFFERENT_FROM_CURRENT_PASSWORD);
        }

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());

        userRepository.updatePassword(userId, newPasswordHash);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException(USER_NOT_FOUND));
    }

    private User findUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }


    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
