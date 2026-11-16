package com.spacebooking.booking.domain;

import com.spacebooking.audit.domain.AuditAction;

/** State pattern (domain.md section 4): one implementation per {@link BookingStatus}. */
public interface BookingState {

    BookingStatus status();

    /** Checks the guard, mutates {@code b} and returns the audit action; illegal pairs throw INVALID_STATE_TRANSITION. */
    AuditAction apply(Booking b, BookingAction a, TransitionInput in);
}
