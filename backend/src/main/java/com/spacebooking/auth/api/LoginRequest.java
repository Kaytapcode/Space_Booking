package com.spacebooking.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** OpenAPI `LoginRequest`. */
public record LoginRequest(
        @NotNull @Email @Size(max = 255) String email,
        @NotNull @Size(min = 1, max = 72) String password) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
