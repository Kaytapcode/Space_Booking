package com.spacebooking.room.service;

import com.spacebooking.booking.infra.BookingRepository;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import com.spacebooking.room.api.RoomRequest;
import com.spacebooking.room.api.RoomResponse;
import com.spacebooking.room.domain.Equipments;
import com.spacebooking.room.domain.Room;
import com.spacebooking.room.domain.RoomChangedEvent;
import com.spacebooking.room.infra.RoomRepository;
import com.spacebooking.room.infra.RoomSpecifications;
import com.spacebooking.security.AuthenticatedUser;
import com.spacebooking.user.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private static final Pattern TOKEN = Pattern.compile("^[a-z0-9-]{1,24}$");

    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoomService(RoomRepository rooms, BookingRepository bookings, ApplicationEventPublisher events,
                       Clock clock) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> search(Integer minCapacity, List<String> equipments, Instant availableFrom,
                                     Instant availableTo, boolean includeInactive) {
        List<FieldError> errors = new ArrayList<>();
        if (minCapacity != null && (minCapacity < 1 || minCapacity > 1000)) {
            errors.add(new FieldError("minCapacity", "must be between 1 and 1000"));
        }
        if (equipments != null && equipments.size() > 20) {
            errors.add(new FieldError("equipments", "must have at most 20 items"));
        }
        if (equipments != null && !equipments.stream().allMatch(t -> t != null && TOKEN.matcher(t).matches())) {
            errors.add(new FieldError("equipments", "tokens must match ^[a-z0-9-]{1,24}$"));
        }
        if ((availableFrom == null) != (availableTo == null)) {
            errors.add(new FieldError(availableFrom == null ? "availableFrom" : "availableTo",
                    "availableFrom and availableTo must be given together"));
        } else if (availableFrom != null && !availableTo.isAfter(availableFrom)) {
            errors.add(new FieldError("availableTo", "must be after availableFrom"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed.", errors);
        }
        Sort sort = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        return rooms.findAll(RoomSpecifications.search(minCapacity, equipments, utc(availableFrom), utc(availableTo),
                includeInactive), sort).stream().map(RoomResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse get(UUID id, AuthenticatedUser actor) {
        Room room = find(id);
        if (!room.isActive() && actor.role() != Role.ADMIN) {
            throw notFound();
        }
        return RoomResponse.from(room);
    }

    @Transactional
    public RoomResponse create(RoomRequest req) {
        if (rooms.existsByName(req.name())) {
            throw duplicateName();
        }
        Room room = rooms.saveAndFlush(new Room(req.name(), req.location(), req.capacity(),
                Equipments.toColumn(req.equipments()), req.requiresApproval()));
        events.publishEvent(new RoomChangedEvent(room.getId()));
        return RoomResponse.from(room);
    }

    /** D19: {@code active} omitted = unchanged, true reactivates, false deactivates with the DELETE guard. */
    @Transactional
    public RoomResponse update(UUID id, RoomRequest req) {
        Room room = find(id);
        if (rooms.existsByNameAndIdNot(req.name(), id)) {
            throw duplicateName();
        }
        room.update(req.name(), req.location(), req.capacity(), Equipments.toColumn(req.equipments()),
                req.requiresApproval());
        if (Boolean.FALSE.equals(req.active())) {
            deactivate(room);
        } else if (Boolean.TRUE.equals(req.active())) {
            room.setActive(true);
        }
        // Flush so a stale version surfaces here as an optimistic failure (409 CONCURRENT_UPDATE).
        rooms.saveAndFlush(room);
        events.publishEvent(new RoomChangedEvent(room.getId()));
        return RoomResponse.from(room);
    }

    /** Idempotent: deactivating an inactive room is a no-op (204 again). */
    @Transactional
    public void deactivate(UUID id) {
        Room room = find(id);
        deactivate(room);
        rooms.saveAndFlush(room);
        events.publishEvent(new RoomChangedEvent(room.getId()));
    }

    private void deactivate(Room room) {
        if (!room.isActive()) {
            return;
        }
        if (bookings.existsBlockingEndingAfter(room.getId(), utc(clock.instant()))) {
            throw new ApiException(ErrorCode.ROOM_HAS_FUTURE_BOOKINGS, "Room has upcoming bookings; cancel them first.");
        }
        room.setActive(false);
    }

    private Room find(UUID id) {
        return rooms.findById(id).orElseThrow(RoomService::notFound);
    }

    private static LocalDateTime utc(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.ROOM_NOT_FOUND, "Room not found.");
    }

    private static ApiException duplicateName() {
        return new ApiException(ErrorCode.ROOM_NAME_ALREADY_EXISTS, "A room with this name already exists.");
    }
}
