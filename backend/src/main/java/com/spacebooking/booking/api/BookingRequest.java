package com.spacebooking.booking.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** OpenAPI `BookingRequest`. Whole-minute and `endTime > startTime` are checked by the service. */
public record BookingRequest(
        @NotNull UUID roomId,
        @NotNull @Size(min = 1, max = 255) String title,
        @NotNull Instant startTime,
        @NotNull Instant endTime) {
}
