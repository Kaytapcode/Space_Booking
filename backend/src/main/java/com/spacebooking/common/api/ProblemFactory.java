package com.spacebooking.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Single place that builds the OpenAPI `ProblemDetail` shape, for MVC and for security-chain responses. */
@Component
public class ProblemFactory {

    private final ObjectMapper objectMapper;

    public ProblemFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProblemDetail build(HttpStatusCode status, ErrorCode code, String detail, HttpServletRequest request,
                               List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (status instanceof HttpStatus known) {
            problem.setTitle(known.getReasonPhrase());
        }
        problem.setInstance(instance(request));
        problem.setProperty("code", code.name());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, ErrorCode code,
                      String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), build(status, code, detail, request, null));
    }

    private static URI instance(HttpServletRequest request) {
        String query = request.getQueryString();
        try {
            return URI.create(request.getRequestURI() + (query == null ? "" : "?" + query));
        } catch (IllegalArgumentException e) {
            // Raw query strings can hold characters URI rejects; the path alone still identifies the call.
            return URI.create("/");
        }
    }
}
