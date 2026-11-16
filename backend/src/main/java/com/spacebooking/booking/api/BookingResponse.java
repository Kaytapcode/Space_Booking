package com.spacebooking.booking.api;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.api.RoomSummary;
import com.spacebooking.user.api.UserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** OpenAPI `BookingResponse`. Needs room and user loaded (entity graph or open transaction). */
public record BookingResponse(UUID id, RoomSummary room, UserSummary user, String title, Instant startTime,
                              Instant endTime, BookingStatus status, @Schema(types = {"string", "null"}) String rejectionReason,
                              Instant createdAt) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), RoomSummary.from(b.getRoom()), UserSummary.from(b.getUser()),
                b.getTitle(), b.getStartTime().toInstant(ZoneOffset.UTC), b.getEndTime().toInstant(ZoneOffset.UTC),
                b.getStatus(), b.getRejectionReason(), b.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
