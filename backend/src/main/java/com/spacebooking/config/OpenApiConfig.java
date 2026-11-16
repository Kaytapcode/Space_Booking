package com.spacebooking.config;

import com.spacebooking.common.api.ApiErrors;
import com.spacebooking.common.api.ProblemDetailSchema;
import com.spacebooking.common.domain.ErrorCode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shapes the springdoc output (used by the contract-conformance check) without touching controller logic. */
@Configuration
class OpenApiConfig {

    private static final String PROBLEM_JSON = "application/problem+json";

    /** One ProblemDetail response per HTTP status, listing the codes from {@link ApiErrors}; 500 always. */
    @Bean
    OperationCustomizer problemResponses() {
        return (operation, handlerMethod) -> {
            Map<Integer, List<ErrorCode>> byStatus = new TreeMap<>();
            byStatus.computeIfAbsent(ErrorCode.INTERNAL_ERROR.status().value(), s -> new ArrayList<>())
                    .add(ErrorCode.INTERNAL_ERROR);
            ApiErrors errors = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (errors != null) {
                for (ErrorCode code : errors.value()) {
                    byStatus.computeIfAbsent(code.status().value(), s -> new ArrayList<>()).add(code);
                }
            }
            byStatus.forEach((status, codes) -> operation.getResponses().addApiResponse(String.valueOf(status),
                    new ApiResponse()
                            .description(codes.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", ")))
                            .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))))));
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer componentSchemas() {
        return openApi -> {
            // ProblemDetail is only referenced by the customizer above, so register it (and FieldError) here.
            ModelConverters.getInstance(true).readAll(ProblemDetailSchema.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            // Response DTOs never omit a property (absent data is an explicit null), so every property is required.
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                boolean response = name.endsWith("Response") || name.endsWith("Summary") || name.endsWith("Page")
                        || name.equals("FieldError");
                if (response && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }
}
