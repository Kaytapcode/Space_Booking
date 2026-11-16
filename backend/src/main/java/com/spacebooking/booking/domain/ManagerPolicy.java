package com.spacebooking.booking.domain;

import com.spacebooking.room.domain.Room;
import java.time.Instant;

public class ManagerPolicy implements BookingPolicyStrategy {

    @Override
    public BookingStatus initialStatus(Room room, Instant start, Instant end) {
        return BookingStatus.CONFIRMED;
    }
}
