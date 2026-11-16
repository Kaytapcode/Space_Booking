package com.spacebooking.user.api;

import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** OpenAPI `UserResponse`. */
public record UserResponse(UUID id, @Schema(format = "email") String email, String fullName, Role role,
                           Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
