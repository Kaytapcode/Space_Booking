package com.spacebooking.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OpenAPI `RejectRequest`. */
public record RejectRequest(@NotBlank @Size(min = 1, max = 255) String reason) {
}
