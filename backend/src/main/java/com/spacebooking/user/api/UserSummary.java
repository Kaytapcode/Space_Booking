package com.spacebooking.user.api;

import com.spacebooking.user.domain.User;
import java.util.UUID;

/** OpenAPI `UserSummary`: public view of a user, never includes the email. */
public record UserSummary(UUID id, String fullName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFullName());
    }
}
