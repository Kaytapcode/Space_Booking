package com.spacebooking.common.domain;

import java.util.List;

/** Business or request failure that maps to a ProblemDetail with a stable code. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> errors;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldError> errors) {
        super(detail);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
