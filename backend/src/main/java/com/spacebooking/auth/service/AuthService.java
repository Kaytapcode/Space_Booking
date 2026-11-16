package com.spacebooking.auth.service;

import com.spacebooking.auth.api.AuthResponse;
import com.spacebooking.auth.api.LoginRequest;
import com.spacebooking.auth.api.RegisterRequest;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.user.api.UserResponse;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import com.spacebooking.user.infra.UserRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;
    private final Clock clock;
    // Verified against for unknown emails so both failures cost the same BCrypt work.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, JwtProperties jwtProps,
                       Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.jwtProps = jwtProps;
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered.");
        }
        // Role is never taken from the request: self-registration yields EMPLOYEE only.
        User user = users.saveAndFlush(
                new User(email, encoder.encode(request.password()), request.fullName(), Role.EMPLOYEE, clock));
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        boolean ok = encoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }
        return response(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwt.issue(user), "Bearer", jwtProps.ttl().toSeconds(), UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
