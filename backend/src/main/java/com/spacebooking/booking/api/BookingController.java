package com.spacebooking.booking.api;

import static com.spacebooking.common.domain.ErrorCode.ACCESS_DENIED;
import static com.spacebooking.common.domain.ErrorCode.BOOKING_ALREADY_STARTED;
import static com.spacebooking.common.domain.ErrorCode.BOOKING_CONFLICT;
import static com.spacebooking.common.domain.ErrorCode.BOOKING_NOT_FOUND;
import static com.spacebooking.common.domain.ErrorCode.CONCURRENT_UPDATE;
import static com.spacebooking.common.domain.ErrorCode.DURATION_EXCEEDED;
import static com.spacebooking.common.domain.ErrorCode.INVALID_STATE_TRANSITION;
import static com.spacebooking.common.domain.ErrorCode.OUTSIDE_CHECKIN_WINDOW;
import static com.spacebooking.common.domain.ErrorCode.ROOM_NOT_FOUND;
import static com.spacebooking.common.domain.ErrorCode.START_IN_PAST;
import static com.spacebooking.common.domain.ErrorCode.UNAUTHENTICATED;
import static com.spacebooking.common.domain.ErrorCode.VALIDATION_FAILED;

import com.spacebooking.audit.api.AuditLogResponse;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.service.BookingService;
import com.spacebooking.common.api.ApiErrors;
import com.spacebooking.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Roles are enforced in SecurityConfig (D29); ownership is checked by BookingService. */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors({VALIDATION_FAILED, START_IN_PAST, DURATION_EXCEEDED, UNAUTHENTICATED, ACCESS_DENIED, ROOM_NOT_FOUND,
            BOOKING_CONFLICT})
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.create(request, actor);
    }

    @GetMapping("/daily-schedule")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED})
    public DailyScheduleResponse getDailySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookings.dailySchedule(date);
    }

    @GetMapping("/my-bookings")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED})
    public BookingPage getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.myBookings(actor, status, page, size);
    }

    @GetMapping("/pending")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED})
    public BookingPage getPendingBookings(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return bookings.pending(page, size);
    }

    @PatchMapping("/{bookingId}/approve")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, BOOKING_NOT_FOUND, INVALID_STATE_TRANSITION,
            BOOKING_ALREADY_STARTED, CONCURRENT_UPDATE})
    public BookingResponse approveBooking(@PathVariable UUID bookingId,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.approve(bookingId, actor);
    }

    @PatchMapping("/{bookingId}/reject")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, BOOKING_NOT_FOUND, INVALID_STATE_TRANSITION,
            BOOKING_ALREADY_STARTED, CONCURRENT_UPDATE})
    public BookingResponse rejectBooking(@PathVariable UUID bookingId, @Valid @RequestBody RejectRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.reject(bookingId, request.reason(), actor);
    }

    @PatchMapping("/{bookingId}/check-in")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, BOOKING_NOT_FOUND, INVALID_STATE_TRANSITION,
            OUTSIDE_CHECKIN_WINDOW, CONCURRENT_UPDATE})
    public BookingResponse checkInBooking(@PathVariable UUID bookingId,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.checkIn(bookingId, actor);
    }

    @PatchMapping("/{bookingId}/checkout")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, BOOKING_NOT_FOUND, INVALID_STATE_TRANSITION,
            CONCURRENT_UPDATE})
    public BookingResponse checkOutBooking(@PathVariable UUID bookingId,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.checkout(bookingId, actor);
    }

    @PatchMapping("/{bookingId}/cancel")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, BOOKING_NOT_FOUND, INVALID_STATE_TRANSITION,
            BOOKING_ALREADY_STARTED, CONCURRENT_UPDATE})
    public BookingResponse cancelBooking(@PathVariable UUID bookingId,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.cancel(bookingId, actor);
    }

    @GetMapping("/{bookingId}/logs")
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED, BOOKING_NOT_FOUND})
    public List<AuditLogResponse> getBookingLogs(@PathVariable UUID bookingId,
                                                 @AuthenticationPrincipal AuthenticatedUser actor) {
        return bookings.logs(bookingId, actor);
    }
}
