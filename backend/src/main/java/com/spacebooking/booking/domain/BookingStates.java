package com.spacebooking.booking.domain;

import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import java.time.Instant;
import java.time.ZoneOffset;

public final class BookingStates {

    private static final BookingState PENDING = new PendingState();
    private static final BookingState CONFIRMED = new ConfirmedState();
    private static final BookingState CHECKED_IN = new CheckedInState();
    private static final BookingState CANCELLED = new CancelledState();
    private static final BookingState NO_SHOW = new NoShowState();
    private static final BookingState COMPLETED = new CompletedState();

    private BookingStates() {
    }

    public static BookingState of(BookingStatus status) {
        return switch (status) {
            case PENDING -> PENDING;
            case CONFIRMED -> CONFIRMED;
            case CHECKED_IN -> CHECKED_IN;
            case CANCELLED -> CANCELLED;
            case NO_SHOW -> NO_SHOW;
            case COMPLETED -> COMPLETED;
        };
    }

    static ApiException invalid(BookingStatus status, BookingAction action) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                "Cannot " + action.verb() + " a booking in status " + status + ".");
    }

    static void requireNotStarted(Booking b, Instant now) {
        if (!now.isBefore(start(b))) {
            throw new ApiException(ErrorCode.BOOKING_ALREADY_STARTED, "The booking has already started.");
        }
    }

    /** Scheduler guards have no dedicated code; an ineligible row is a lost race, so the scheduler skips it. */
    static void requireSystemGuard(boolean ok, BookingStatus status, BookingAction action) {
        if (!ok) {
            throw invalid(status, action);
        }
    }

    static Instant start(Booking b) {
        return b.getStartTime().toInstant(ZoneOffset.UTC);
    }

    static Instant end(Booking b) {
        return b.getEndTime().toInstant(ZoneOffset.UTC);
    }
}
