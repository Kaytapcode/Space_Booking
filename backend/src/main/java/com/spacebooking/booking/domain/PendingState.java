package com.spacebooking.booking.domain;

import com.spacebooking.audit.domain.AuditAction;

public class PendingState implements BookingState {

    @Override
    public BookingStatus status() {
        return BookingStatus.PENDING;
    }

    @Override
    public AuditAction apply(Booking b, BookingAction a, TransitionInput in) {
        switch (a) {
            case APPROVE -> {
                BookingStates.requireNotStarted(b, in.now());
                b.setStatus(BookingStatus.CONFIRMED);
                return AuditAction.APPROVED;
            }
            case REJECT -> {
                BookingStates.requireNotStarted(b, in.now());
                b.setRejectionReason(in.reason());
                b.setStatus(BookingStatus.CANCELLED);
                return AuditAction.REJECTED;
            }
            case CANCEL -> {
                BookingStates.requireNotStarted(b, in.now());
                b.setStatus(BookingStatus.CANCELLED);
                return AuditAction.CANCELLED;
            }
            case EXPIRE -> {
                BookingStates.requireSystemGuard(!in.now().isBefore(BookingStates.start(b)), status(), a);
                b.setStatus(BookingStatus.CANCELLED);
                return AuditAction.EXPIRED;
            }
            default -> throw BookingStates.invalid(status(), a);
        }
    }
}
