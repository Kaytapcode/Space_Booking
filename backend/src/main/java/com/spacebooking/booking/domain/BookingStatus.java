package com.spacebooking.booking.domain;

import java.util.Set;

public enum BookingStatus {
    PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW;

    /** Statuses that occupy a room (domain.md section 2). */
    public static final Set<BookingStatus> BLOCKING = Set.of(PENDING, CONFIRMED, CHECKED_IN);
}
