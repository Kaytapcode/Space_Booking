package com.spacebooking.room.api;

import com.spacebooking.room.domain.Equipments;
import com.spacebooking.room.domain.Room;
import java.util.List;
import java.util.UUID;

/** OpenAPI `RoomResponse`. */
public record RoomResponse(UUID id, String name, String location, int capacity, List<String> equipments,
                           boolean requiresApproval, boolean active) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getLocation(), room.getCapacity(),
                Equipments.fromColumn(room.getEquipments()), room.isRequiresApproval(), room.isActive());
    }
}
