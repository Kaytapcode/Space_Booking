package com.spacebooking.booking.service;

import com.spacebooking.audit.api.AuditLogResponse;
import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.audit.infra.AuditLogRepository;
import com.spacebooking.booking.api.BookingPage;
import com.spacebooking.booking.api.BookingRequest;
import com.spacebooking.booking.api.BookingResponse;
import com.spacebooking.booking.api.DailyScheduleResponse;
import com.spacebooking.booking.api.RoomScheduleResponse;
import com.spacebooking.booking.api.ScheduleBookingResponse;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingAction;
import com.spacebooking.booking.domain.BookingPolicyResolver;
import com.spacebooking.booking.domain.BookingPolicyStrategy;
import com.spacebooking.booking.domain.BookingStates;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.domain.BookingStatusChangedEvent;
import com.spacebooking.booking.domain.TransitionInput;
import com.spacebooking.booking.infra.BookingRepository;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import com.spacebooking.room.api.RoomResponse;
import com.spacebooking.room.domain.Room;
import com.spacebooking.room.infra.RoomRepository;
import com.spacebooking.security.AuthenticatedUser;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    public static final String SYSTEM_ACTOR = "SYSTEM_SCHEDULER";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(BookingRepository bookings, RoomRepository rooms, UserRepository users,
                          AuditLogRepository auditLogs, ApplicationEventPublisher events, Clock clock) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.users = users;
        this.auditLogs = auditLogs;
        this.events = events;
        this.clock = clock;
    }

    /** Creation protocol, domain.md section 7. */
    @Transactional
    public BookingResponse create(BookingRequest req, AuthenticatedUser actor) {
        BookingPolicyStrategy policy = BookingPolicyResolver.forRole(actor.role());
        validateTimes(req.startTime(), req.endTime());
        // OPTIMISTIC_FORCE_INCREMENT: the commit bumps rooms.version, so a concurrent creator on this room fails.
        Room room = rooms.findByIdForBooking(req.roomId()).filter(Room::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.ROOM_NOT_FOUND, "Room not found."));
        Instant now = clock.instant();
        if (req.startTime().isBefore(now.truncatedTo(ChronoUnit.MINUTES))) {
            throw new ApiException(ErrorCode.START_IN_PAST, "Start time is in the past.");
        }
        BookingStatus status = policy.initialStatus(room, req.startTime(), req.endTime());
        LocalDateTime start = utc(req.startTime());
        LocalDateTime end = utc(req.endTime());
        if (bookings.existsOverlap(room.getId(), start, end)) {
            throw new ApiException(ErrorCode.BOOKING_CONFLICT, "The room is already booked in this time range.");
        }
        // saveAndFlush so the exclusion-constraint backstop fires here, not after the method returns.
        Booking booking = bookings.saveAndFlush(
                new Booking(room, users.findById(actor.id()).orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHENTICATED, "The account of this token no longer exists.")),
                        req.title(), start, end, status, clock));
        events.publishEvent(new BookingStatusChangedEvent(booking.getId(), null, status, AuditAction.CREATED,
                actor.email(), null, now));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse approve(UUID id, AuthenticatedUser actor) {
        return transition(lock(id), BookingAction.APPROVE, null, actor.email());
    }

    @Transactional
    public BookingResponse reject(UUID id, String reason, AuthenticatedUser actor) {
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed.",
                    List.of(new FieldError("reason", "must be 1-255 characters and not blank")));
        }
        return transition(lock(id), BookingAction.REJECT, reason, actor.email());
    }

    @Transactional
    public BookingResponse cancel(UUID id, AuthenticatedUser actor) {
        return transition(lockOwned(id, actor), BookingAction.CANCEL, null, actor.email());
    }

    @Transactional
    public BookingResponse checkIn(UUID id, AuthenticatedUser actor) {
        return transition(lockOwned(id, actor), BookingAction.CHECK_IN, null, actor.email());
    }

    @Transactional
    public BookingResponse checkout(UUID id, AuthenticatedUser actor) {
        return transition(lockOwned(id, actor), BookingAction.CHECKOUT, null, actor.email());
    }

    /** Scheduler entry point (Phase 4): one transaction per booking; an ineligible row throws and is skipped. */
    @Transactional
    public BookingResponse systemTransition(UUID id, BookingAction action) {
        return transition(lock(id), action, null, SYSTEM_ACTOR);
    }

    @Transactional(readOnly = true)
    public BookingPage myBookings(AuthenticatedUser actor, BookingStatus status, int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Order.desc("startTime"), Sort.Order.asc("id")));
        var result = status == null
                ? bookings.findByUserId(actor.id(), p)
                : bookings.findByUserIdAndStatus(actor.id(), status, p);
        return BookingPage.from(result.map(BookingResponse::from));
    }

    @Transactional(readOnly = true)
    public BookingPage pending(int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id")));
        return BookingPage.from(bookings.findByStatus(BookingStatus.PENDING, p).map(BookingResponse::from));
    }

    /** Evicted after commit by {@link ScheduleCacheEvictor}. */
    @Cacheable(ScheduleCacheEvictor.CACHE)
    @Transactional(readOnly = true)
    public DailyScheduleResponse dailySchedule(LocalDate date) {
        LocalDateTime dayStart = utc(date.atStartOfDay(BUSINESS_ZONE).toInstant());
        LocalDateTime dayEnd = utc(date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
        // Repository returns start asc, id asc; groupingBy into lists keeps that order per room.
        Map<UUID, List<ScheduleBookingResponse>> byRoom = bookings.findForSchedule(dayStart, dayEnd).stream()
                .collect(Collectors.groupingBy(b -> b.getRoom().getId(),
                        Collectors.mapping(ScheduleBookingResponse::from, Collectors.toList())));
        List<RoomScheduleResponse> schedule = rooms.findByActiveTrueOrderByNameAscIdAsc().stream()
                .map(room -> new RoomScheduleResponse(RoomResponse.from(room),
                        byRoom.getOrDefault(room.getId(), List.of())))
                .toList();
        return new DailyScheduleResponse(date, schedule);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> logs(UUID id, AuthenticatedUser actor) {
        Booking booking = bookings.findById(id).orElseThrow(BookingService::notFound);
        if (actor.role() == Role.EMPLOYEE && !booking.getUser().getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You can only view logs of your own bookings.");
        }
        return auditLogs.findByBookingIdOrderByCreatedAtAscIdAsc(id).stream().map(AuditLogResponse::from).toList();
    }

    private Booking lock(UUID id) {
        bookings.applyLockTimeout();
        return bookings.findByIdForUpdate(id).orElseThrow(BookingService::notFound);
    }

    private Booking lockOwned(UUID id, AuthenticatedUser actor) {
        Booking booking = lock(id);
        if (!booking.getUser().getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only the owner of this booking can do this.");
        }
        return booking;
    }

    private BookingResponse transition(Booking booking, BookingAction action, String reason, String performedBy) {
        BookingStatus from = booking.getStatus();
        Instant now = clock.instant();
        AuditAction audit = BookingStates.of(from).apply(booking, action, new TransitionInput(now, reason));
        String details = switch (audit) {
            case REJECTED -> reason;
            case CHECKED_OUT -> booking.getEndTime().toInstant(ZoneOffset.UTC).toString();
            default -> null;
        };
        events.publishEvent(new BookingStatusChangedEvent(booking.getId(), from, booking.getStatus(), audit,
                performedBy, details, now));
        return BookingResponse.from(booking);
    }

    private static void validateTimes(Instant start, Instant end) {
        List<FieldError> errors = new ArrayList<>();
        wholeMinute("startTime", start, errors);
        wholeMinute("endTime", end, errors);
        if (errors.isEmpty() && !end.isAfter(start)) {
            errors.add(new FieldError("endTime", "must be after startTime"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed.", errors);
        }
    }

    private static void wholeMinute(String field, Instant t, List<FieldError> errors) {
        if (t == null) {
            errors.add(new FieldError(field, "must not be null"));
        } else if (!t.truncatedTo(ChronoUnit.MINUTES).equals(t)) {
            errors.add(new FieldError(field, "must be a whole minute"));
        }
    }

    private static LocalDateTime utc(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneOffset.UTC);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.BOOKING_NOT_FOUND, "Booking not found.");
    }
}
