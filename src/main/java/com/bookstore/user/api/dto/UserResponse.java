package com.bookstore.user.api.dto;

import com.bookstore.user.domain.UserRole;

import java.time.OffsetDateTime;

public class UserResponse {
    private final Long id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final UserRole role;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public UserResponse(
            Long id,
            String email,
            String firstName,
            String lastName,
            UserRole role,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserRole getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
