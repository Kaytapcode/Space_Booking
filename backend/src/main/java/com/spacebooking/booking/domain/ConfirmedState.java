package com.spacebooking.booking.domain;

import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;

public class ConfirmedState implements BookingState {

    private static final Duration CHECKIN_WINDOW = Duration.ofMinutes(15);

    @Override
    public BookingStatus status() {
        return BookingStatus.CONFIRMED;
    }

    @Override
    public AuditAction apply(Booking b, BookingAction a, TransitionInput in) {
        Instant start = BookingStates.start(b);
        switch (a) {
            case CHECK_IN -> {
                if (in.now().isBefore(start.minus(CHECKIN_WINDOW)) || in.now().isAfter(start.plus(CHECKIN_WINDOW))) {
                    throw new ApiException(ErrorCode.OUTSIDE_CHECKIN_WINDOW,
                            "Check-in is allowed from 15 minutes before to 15 minutes after the start time.");
                }
                b.setStatus(BookingStatus.CHECKED_IN);
                return AuditAction.CHECKED_IN;
            }
            case CANCEL -> {
                BookingStates.requireNotStarted(b, in.now());
                b.setStatus(BookingStatus.CANCELLED);
                return AuditAction.CANCELLED;
            }
            case MARK_NO_SHOW -> {
                BookingStates.requireSystemGuard(in.now().isAfter(start.plus(CHECKIN_WINDOW)), status(), a);
                b.setStatus(BookingStatus.NO_SHOW);
                return AuditAction.NO_SHOW;
            }
            default -> throw BookingStates.invalid(status(), a);
        }
    }
}
