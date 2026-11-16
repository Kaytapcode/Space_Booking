package com.spacebooking.common.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import jakarta.persistence.LockTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;

/** The only mapping from exceptions to ProblemDetail responses. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_DETAIL = "Request validation failed.";
    /** PostgreSQL exclusion_violation: the `ex_bookings_no_overlap` backstop. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    private final ProblemFactory problems;

    public GlobalExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException e, HttpServletRequest request) {
        return respond(e.getCode().status(), e.getCode(), e.getMessage(), request, e.getErrors(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<FieldError> errors = new ArrayList<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.add(new FieldError(f.getField(), f.getDefaultMessage())));
        e.getBindingResult().getGlobalErrors()
                .forEach(g -> errors.add(new FieldError(g.getObjectName(), g.getDefaultMessage())));
        return validation(request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException e,
                                                         HttpServletRequest request) {
        List<FieldError> errors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            if (result instanceof ParameterErrors pe) {
                pe.getFieldErrors().forEach(f -> errors.add(new FieldError(f.getField(), f.getDefaultMessage())));
            } else {
                result.getResolvableErrors()
                        .forEach(r -> errors.add(new FieldError(name, r.getDefaultMessage())));
            }
        });
        return validation(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e,
                                                            HttpServletRequest request) {
        List<FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                     HttpServletRequest request) {
        return validation(request, List.of(new FieldError(e.getName(), "has an invalid value")));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    ResponseEntity<ProblemDetail> handleBinding(ServletRequestBindingException e, HttpServletRequest request) {
        // Covers missing request parameters and headers.
        String field = e instanceof MissingServletRequestParameterException m ? m.getParameterName() : "request";
        return validation(request, List.of(new FieldError(field, "is required")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        FieldError error = new FieldError("body", "malformed JSON request");
        if (e.getCause() instanceof JsonMappingException jme) {
            String path = jme.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining("."));
            String message = jme instanceof UnrecognizedPropertyException ? "unknown property" : "has an invalid value";
            error = new FieldError(path.isEmpty() ? "body" : path, message);
        }
        return validation(request, List.of(error));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(Exception e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.REQUEST_NOT_SUPPORTED, "No such resource.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (e.getSupportedHttpMethods() != null) {
            headers.setAllow(e.getSupportedHttpMethods());
        }
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.REQUEST_NOT_SUPPORTED, "Method not allowed.",
                request, null, headers);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.REQUEST_NOT_SUPPORTED,
                "Unsupported media type.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> handleNotAcceptable(HttpMediaTypeNotAcceptableException e,
                                                      HttpServletRequest request) {
        return respond(HttpStatus.NOT_ACCEPTABLE, ErrorCode.REQUEST_NOT_SUPPORTED, "Not acceptable.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication is required.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "You are not allowed to perform this action.",
                request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException e,
                                                       HttpServletRequest request) {
        return creationRaceOrConcurrentUpdate(request);
    }

    // Creation takes no pessimistic lock, so a lock failure there is the deadlock Postgres raises between
    // concurrent inserts checked by ex_bookings_no_overlap: the same slot race as the backstop (domain.md 7.6).
    @ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class})
    ResponseEntity<ProblemDetail> handleLockTimeout(Exception e, HttpServletRequest request) {
        return creationRaceOrConcurrentUpdate(request);
    }

    private ResponseEntity<ProblemDetail> creationRaceOrConcurrentUpdate(HttpServletRequest request) {
        // Only the race of booking creation is a slot conflict (D22); other endpoints lost an update.
        if ("POST".equals(request.getMethod()) && "/api/v1/bookings".equals(request.getRequestURI())) {
            return respond(HttpStatus.CONFLICT, ErrorCode.BOOKING_CONFLICT,
                    "The slot was taken by a concurrent request.", request);
        }
        return concurrentUpdate(request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (hasSqlState(e, EXCLUSION_VIOLATION) || cause.contains("bookings_no_overlap")) {
            return respond(HttpStatus.CONFLICT, ErrorCode.BOOKING_CONFLICT,
                    "The slot overlaps an existing booking.", request);
        }
        if (cause.contains("uq_users_email")) {
            return respond(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered.",
                    request);
        }
        if (cause.contains("uq_rooms_name")) {
            return respond(HttpStatus.CONFLICT, ErrorCode.ROOM_NAME_ALREADY_EXISTS,
                    "Another room already has this name.", request);
        }
        return internal(e, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        return internal(e, request);
    }

    private ResponseEntity<ProblemDetail> concurrentUpdate(HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_UPDATE,
                "A concurrent change won; reload and retry.", request);
    }

    private static boolean hasSqlState(Throwable e, String sqlState) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ProblemDetail> internal(Exception e, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.",
                request);
    }

    private ResponseEntity<ProblemDetail> validation(HttpServletRequest request, List<FieldError> errors) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_DETAIL, request, errors, null);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatusCode status, ErrorCode code, String detail,
                                                  HttpServletRequest request) {
        return respond(status, code, detail, request, null, null);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatusCode status, ErrorCode code, String detail,
                                                  HttpServletRequest request, List<FieldError> errors,
                                                  HttpHeaders headers) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(problems.build(status, code, detail, request, errors));
    }
}
