package com.spacebooking.room.api;

import com.spacebooking.common.api.UniqueItems;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** OpenAPI `RoomRequest`. `active`: ignored on create; on update omitted = unchanged (D19). */
public record RoomRequest(
        @NotNull @Size(min = 1, max = 100) String name,
        @NotNull @Size(min = 1, max = 255) String location,
        @NotNull @Min(1) @Max(1000) Integer capacity,
        @NotNull @Size(max = 20) @UniqueItems List<@NotNull @Pattern(regexp = "^[a-z0-9-]{1,24}$") String> equipments,
        @NotNull Boolean requiresApproval,
        Boolean active) {
}
