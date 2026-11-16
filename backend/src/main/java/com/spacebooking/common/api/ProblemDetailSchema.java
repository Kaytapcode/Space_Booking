package com.spacebooking.common.api;

import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Documentation-only twin of the ProblemDetail JSON that {@link ProblemFactory} builds (Spring's ProblemDetail is a
 * property bag, so springdoc cannot derive the shape). Never instantiated.
 */
@Schema(name = "ProblemDetail", description = "RFC 7807 problem with a stable `code`.")
public record ProblemDetailSchema(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, defaultValue = "about:blank") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        String detail,
        String instance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ErrorCode code,
        @Schema(description = "Present only for VALIDATION_FAILED.") List<FieldError> errors) {
}
