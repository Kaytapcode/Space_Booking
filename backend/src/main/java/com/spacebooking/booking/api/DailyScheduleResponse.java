package com.spacebooking.booking.api;

import java.time.LocalDate;
import java.util.List;

/** OpenAPI `DailyScheduleResponse`. Immutable, so it is safe to cache. */
public record DailyScheduleResponse(LocalDate date, List<RoomScheduleResponse> rooms) {
}
