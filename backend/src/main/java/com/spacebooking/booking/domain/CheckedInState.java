package com.spacebooking.booking.domain;

import com.spacebooking.audit.domain.AuditAction;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class CheckedInState implements BookingState {

    @Override
    public BookingStatus status() {
        return BookingStatus.CHECKED_IN;
    }

    @Override
    public AuditAction apply(Booking b, BookingAction a, TransitionInput in) {
        switch (a) {
            case CHECKOUT -> {
                // D17: end = min(end, max(start + 1m, ceilToMinute(now)))
                Instant end = BookingStates.end(b);
                Instant floor = BookingStates.start(b).plus(1, ChronoUnit.MINUTES);
                Instant newEnd = min(end, max(floor, ceilToMinute(in.now())));
                b.setEndTime(LocalDateTime.ofInstant(newEnd, ZoneOffset.UTC));
                b.setStatus(BookingStatus.COMPLETED);
                return AuditAction.CHECKED_OUT;
            }
            case COMPLETE -> {
                BookingStates.requireSystemGuard(!in.now().isBefore(BookingStates.end(b)), status(), a);
                b.setStatus(BookingStatus.COMPLETED);
                return AuditAction.COMPLETED;
            }
            default -> throw BookingStates.invalid(status(), a);
        }
    }

    private static Instant ceilToMinute(Instant t) {
        Instant floor = t.truncatedTo(ChronoUnit.MINUTES);
        return floor.equals(t) ? t : floor.plus(1, ChronoUnit.MINUTES);
    }

    private static Instant min(Instant x, Instant y) {
        return x.isBefore(y) ? x : y;
    }

    private static Instant max(Instant x, Instant y) {
        return x.isAfter(y) ? x : y;
    }
}
