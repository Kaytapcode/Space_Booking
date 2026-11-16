package com.spacebooking.booking.api;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.room.api.RoomResponse;
import com.spacebooking.room.domain.Room;
import java.util.List;

/** OpenAPI `RoomScheduleResponse`. */
public record RoomScheduleResponse(RoomResponse room, List<ScheduleBookingResponse> bookings) {

    public static RoomScheduleResponse from(Room room, List<Booking> bookings) {
        return new RoomScheduleResponse(RoomResponse.from(room),
                bookings.stream().map(ScheduleBookingResponse::from).toList());
    }
}
