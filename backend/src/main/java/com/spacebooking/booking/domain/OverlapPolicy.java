package com.spacebooking.booking.domain;

import java.time.Instant;

/** domain.md section 2 on half-open intervals; the repository query applies the same predicate in SQL. */
public final class OverlapPolicy {

    private OverlapPolicy() {
    }

    public static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
