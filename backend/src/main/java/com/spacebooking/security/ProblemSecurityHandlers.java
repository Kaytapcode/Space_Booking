package com.spacebooking.security;

import com.spacebooking.common.api.ProblemFactory;
import com.spacebooking.common.domain.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Security-chain 401/403 in the same ProblemDetail shape as the MVC advice. */
final class ProblemSecurityHandlers {

    private ProblemSecurityHandlers() {
    }

    static AuthenticationEntryPoint entryPoint(ProblemFactory problems) {
        return (request, response, e) -> problems.write(request, response, HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }

    static AccessDeniedHandler accessDenied(ProblemFactory problems) {
        return (request, response, e) -> problems.write(request, response, HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED, "You are not allowed to perform this action.");
    }
}
