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

    // In case a user submits the same request twice:
    // the DB is secured from concurrency - the final state will be as expected, BUT
    // the HTTP response may differ - the 1st successful request will return '204 No Content'
    // - if the 2nd request runs parallel to the 1st - second response will be '409 Conflict' (FOR UPDATE NOWAIT)
    // - if the 2nd request runs after the 1st - second response will be '401 Unauthorized' (current password will no longer match)
    // solution: idempotent requests
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
