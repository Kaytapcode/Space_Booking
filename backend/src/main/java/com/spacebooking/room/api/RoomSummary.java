package com.spacebooking.room.api;

import com.spacebooking.room.domain.Room;
import java.util.UUID;

/** OpenAPI `RoomSummary`. */
public record RoomSummary(UUID id, String name, String location) {

    public static RoomSummary from(Room room) {
        return new RoomSummary(room.getId(), room.getName(), room.getLocation());
    }
}
