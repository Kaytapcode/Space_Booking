package com.spacebooking.security;

import com.spacebooking.user.domain.Role;
import java.util.UUID;

/** Principal rebuilt from verified token claims; no database hit per request. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
