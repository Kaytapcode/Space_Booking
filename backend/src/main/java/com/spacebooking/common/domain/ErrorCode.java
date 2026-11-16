package com.spacebooking.common.domain;

import org.springframework.http.HttpStatus;

/** Mirrors the OpenAPI `ErrorCode` catalog (code -> HTTP status). */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    START_IN_PAST(HttpStatus.BAD_REQUEST),
    DURATION_EXCEEDED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ROOM_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT),
    ROOM_HAS_FUTURE_BOOKINGS(HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    BOOKING_CONFLICT(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    BOOKING_ALREADY_STARTED(HttpStatus.CONFLICT),
    OUTSIDE_CHECKIN_WINDOW(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    // 404/405/415 at framework level; the actual status is chosen by the exception handler.
    REQUEST_NOT_SUPPORTED(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
