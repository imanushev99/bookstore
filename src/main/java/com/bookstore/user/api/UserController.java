package com.bookstore.user.api;

import com.bookstore.common.security.SecurityUtils;
import com.bookstore.user.api.dto.ChangePasswordRequest;
import com.bookstore.user.api.dto.UpdateProfileRequest;
import com.bookstore.user.api.dto.UserResponse;
import com.bookstore.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();

        UserResponse response = userService.getProfile(userId);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        UserResponse response = userService.updateProfile(userId, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        userService.changePassword(userId, request);

        return ResponseEntity.noContent().build();
    }

    /**
     * changingPassword requires additional request before the change password - requestPasswordChange
     * It returns 200 only after it sends an email with a link, from where we call the changePassword.
     */
}
