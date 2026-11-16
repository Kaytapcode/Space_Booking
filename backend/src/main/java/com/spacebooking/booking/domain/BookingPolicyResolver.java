package com.spacebooking.booking.domain;

import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.user.domain.Role;

public final class BookingPolicyResolver {

    private static final BookingPolicyStrategy EMPLOYEE = new EmployeePolicy();
    private static final BookingPolicyStrategy MANAGER = new ManagerPolicy();

    private BookingPolicyResolver() {
    }

    public static BookingPolicyStrategy forRole(Role role) {
        return switch (role) {
            case EMPLOYEE -> EMPLOYEE;
            case MANAGER -> MANAGER;
            case ADMIN -> throw new ApiException(ErrorCode.ACCESS_DENIED, "Administrators cannot create bookings.");
        };
    }
}
