package com.spacebooking.auth.service;

import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, String email, Role role, Instant issuedAt, Instant expiresAt) {
}
