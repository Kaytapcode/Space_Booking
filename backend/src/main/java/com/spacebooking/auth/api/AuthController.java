package com.spacebooking.auth.api;

import static com.spacebooking.common.domain.ErrorCode.EMAIL_ALREADY_EXISTS;
import static com.spacebooking.common.domain.ErrorCode.INVALID_CREDENTIALS;
import static com.spacebooking.common.domain.ErrorCode.UNAUTHENTICATED;
import static com.spacebooking.common.domain.ErrorCode.VALIDATION_FAILED;

import com.spacebooking.auth.service.AuthService;
import com.spacebooking.common.api.ApiErrors;
import com.spacebooking.security.AuthenticatedUser;
import com.spacebooking.user.api.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors({VALIDATION_FAILED, EMAIL_ALREADY_EXISTS})
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    @ApiErrors({VALIDATION_FAILED, INVALID_CREDENTIALS})
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    @ApiErrors(UNAUTHENTICATED)
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return auth.currentUser(principal.id());
    }
}
