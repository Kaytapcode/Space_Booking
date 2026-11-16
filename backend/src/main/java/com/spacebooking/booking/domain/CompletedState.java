package com.spacebooking.booking.domain;

import com.spacebooking.audit.domain.AuditAction;

/** Terminal. */
public class CompletedState implements BookingState {

    @Override
    public BookingStatus status() {
        return BookingStatus.COMPLETED;
    }

    @Override
    public AuditAction apply(Booking b, BookingAction a, TransitionInput in) {
        throw BookingStates.invalid(status(), a);
    }
}
