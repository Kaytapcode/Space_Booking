package com.spacebooking.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** OpenAPI `RegisterRequest`. The email is trimmed before format validation. */
public record RegisterRequest(
        @NotNull @Email @Size(max = 255) String email,
        @NotNull @Size(min = 8, max = 72) String password,
        @NotNull @Size(min = 1, max = 100) String fullName) {

    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
