package com.spacebooking.room.api;

import static com.spacebooking.common.domain.ErrorCode.ACCESS_DENIED;
import static com.spacebooking.common.domain.ErrorCode.CONCURRENT_UPDATE;
import static com.spacebooking.common.domain.ErrorCode.ROOM_HAS_FUTURE_BOOKINGS;
import static com.spacebooking.common.domain.ErrorCode.ROOM_NAME_ALREADY_EXISTS;
import static com.spacebooking.common.domain.ErrorCode.ROOM_NOT_FOUND;
import static com.spacebooking.common.domain.ErrorCode.UNAUTHENTICATED;
import static com.spacebooking.common.domain.ErrorCode.VALIDATION_FAILED;

import com.spacebooking.common.api.ApiErrors;
import com.spacebooking.room.service.RoomService;
import com.spacebooking.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    // Roles are enforced in SecurityConfig (D29). Param ranges are validated in RoomService, not with @Min here.
    @GetMapping
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED})
    public List<RoomResponse> searchRooms(
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "1000"))
            @RequestParam(required = false) Integer minCapacity,
            @Parameter(description = "Comma-separated tokens, e.g. `tv,whiteboard`.", style = ParameterStyle.FORM,
                    explode = Explode.FALSE,
                    array = @ArraySchema(maxItems = 20, schema = @Schema(pattern = "^[a-z0-9-]{1,24}$")))
            @RequestParam(required = false) List<String> equipments,
            @RequestParam(required = false) Instant availableFrom,
            @RequestParam(required = false) Instant availableTo,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return rooms.search(minCapacity, equipments, availableFrom, availableTo, includeInactive);
    }

    @GetMapping("/{roomId}")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ROOM_NOT_FOUND})
    public RoomResponse getRoom(@PathVariable UUID roomId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return rooms.get(roomId, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, ROOM_NAME_ALREADY_EXISTS})
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        RoomResponse created = rooms.create(request);
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + created.id())).body(created);
    }

    @PutMapping("/{roomId}")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, ROOM_NOT_FOUND, ROOM_NAME_ALREADY_EXISTS,
            ROOM_HAS_FUTURE_BOOKINGS, CONCURRENT_UPDATE})
    public RoomResponse updateRoom(@PathVariable UUID roomId, @Valid @RequestBody RoomRequest request) {
        return rooms.update(roomId, request);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, ROOM_NOT_FOUND, ROOM_HAS_FUTURE_BOOKINGS,
            CONCURRENT_UPDATE})
    public void deactivateRoom(@PathVariable UUID roomId) {
        rooms.deactivate(roomId);
    }
}
