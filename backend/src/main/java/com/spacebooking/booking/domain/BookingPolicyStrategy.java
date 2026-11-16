package com.spacebooking.booking.domain;

import com.spacebooking.room.domain.Room;
import java.time.Instant;

/** Strategy (domain.md section 3): duration limit and initial status by role. */
public interface BookingPolicyStrategy {

    /** Throws DURATION_EXCEEDED when the slot is longer than the policy allows. */
    BookingStatus initialStatus(Room room, Instant start, Instant end);
}
