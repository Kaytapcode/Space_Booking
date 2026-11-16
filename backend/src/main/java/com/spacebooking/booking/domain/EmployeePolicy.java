package com.spacebooking.booking.domain;

import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.room.domain.Room;
import java.time.Duration;
import java.time.Instant;

public class EmployeePolicy implements BookingPolicyStrategy {

    private static final Duration MAX = Duration.ofMinutes(120);

    @Override
    public BookingStatus initialStatus(Room room, Instant start, Instant end) {
        if (Duration.between(start, end).compareTo(MAX) > 0) {
            throw new ApiException(ErrorCode.DURATION_EXCEEDED, "Employees can book at most 120 minutes.");
        }
        return room.isRequiresApproval() ? BookingStatus.PENDING : BookingStatus.CONFIRMED;
    }
}
