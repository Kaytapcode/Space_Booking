package com.spacebooking.auth.api;

import com.spacebooking.user.api.UserResponse;

/** OpenAPI `AuthResponse`; `tokenType` is always "Bearer". */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
