package com.spacebooking.booking.api;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.user.api.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** OpenAPI `ScheduleBookingResponse`: only blocking statuses appear in a schedule. */
public record ScheduleBookingResponse(UUID id, String title, Instant startTime, Instant endTime,
                                      @Schema(allowableValues = {"PENDING", "CONFIRMED", "CHECKED_IN"})
                                      BookingStatus status, UserSummary user) {

    public static ScheduleBookingResponse from(Booking b) {
        return new ScheduleBookingResponse(b.getId(), b.getTitle(), b.getStartTime().toInstant(ZoneOffset.UTC),
                b.getEndTime().toInstant(ZoneOffset.UTC), b.getStatus(), UserSummary.from(b.getUser()));
    }
}
