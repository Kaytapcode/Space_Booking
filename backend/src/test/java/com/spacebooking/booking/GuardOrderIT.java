package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * R54 (domain.md section 4, D29): auth 401 -> role 403 -> request shape 400 (incl. path UUIDs) -> exists 404
 * -> owner 403 -> state 409 -> time guard 409.
 */
class GuardOrderIT extends AbstractDomainIT {

  private static final Instant START = inMinutes(120);
  private static final Instant END = START.plus(60, ChronoUnit.MINUTES);
  private static final String BAD_BODY = "{\"roomId\":\"not-a-uuid\",\"title\":\"\"}";

  enum Case {
    NO_TOKEN_BAD_BODY,
    NO_TOKEN_MALFORMED_UUID,
    EMPLOYEE_APPROVE_UNKNOWN_ID,
    EMPLOYEE_APPROVE_MALFORMED_UUID,
    ADMIN_CREATE_UNKNOWN_ROOM,
    ADMIN_CREATE_BAD_BODY,
    EMPLOYEE_PENDING_BAD_PARAM,
    ADMIN_MY_BOOKINGS_BAD_PARAM,
    EMPLOYEE_ROOM_POST_BAD_BODY,
    ADMIN_ROOM_POST_BAD_BODY,
    MANAGER_BAD_BODY_UNKNOWN_ROOM,
    MANAGER_APPROVE_MALFORMED_UUID,
    MANAGER_REJECT_BLANK_REASON_UNKNOWN_ID,
    MANAGER_APPROVE_UNKNOWN_ID,
    EMPLOYEE_CANCEL_UNKNOWN_ID,
    NON_OWNER_ON_TERMINAL,
    OWNER_ON_TERMINAL,
    OWNER_CHECKIN_TERMINAL_OUTSIDE_WINDOW,
    OWNER_CANCEL_COMPLETED_AFTER_START,
    OWNER_CANCEL_CHECKED_IN_AFTER_START,
    EMPLOYEE_INCLUDE_INACTIVE_BAD_CAPACITY,
    ADMIN_INCLUDE_INACTIVE_BAD_CAPACITY
  }

  private Booking terminal(Actor owner, BookingStatus status) {
    return booking(room(false), owner, START, END, status);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Case.class)
  void guardOrder(Case c) {
    Actor employee = actor(Role.EMPLOYEE);
    Actor other = actor(Role.EMPLOYEE);
    Actor manager = actor(Role.MANAGER);
    Actor admin = actor(Role.ADMIN);
    UUID unknown = UUID.randomUUID();

    switch (c) {
      case NO_TOKEN_BAD_BODY -> assertProblem(as(null, "POST", BOOKINGS, BAD_BODY), 401, "UNAUTHENTICATED");
      case NO_TOKEN_MALFORMED_UUID -> assertProblem(patch(null, BOOKINGS + "/not-a-uuid/approve"), 401, "UNAUTHENTICATED");
      case EMPLOYEE_APPROVE_UNKNOWN_ID -> assertProblem(patch(employee, BOOKINGS + "/" + unknown + "/approve"), 403, "ACCESS_DENIED");
      case EMPLOYEE_APPROVE_MALFORMED_UUID -> assertProblem(patch(employee, BOOKINGS + "/not-a-uuid/approve"), 403, "ACCESS_DENIED");
      case ADMIN_CREATE_UNKNOWN_ROOM ->
          assertProblem(postJson(admin, BOOKINGS, bookingBody(unknown, START, END)), 403, "ACCESS_DENIED");
      case ADMIN_CREATE_BAD_BODY -> assertProblem(as(admin, "POST", BOOKINGS, BAD_BODY), 403, "ACCESS_DENIED");
      case EMPLOYEE_PENDING_BAD_PARAM -> assertProblem(get(employee, BOOKINGS + "/pending?size=500"), 403, "ACCESS_DENIED");
      case ADMIN_MY_BOOKINGS_BAD_PARAM -> assertProblem(get(admin, BOOKINGS + "/my-bookings?size=500"), 403, "ACCESS_DENIED");
      case EMPLOYEE_ROOM_POST_BAD_BODY -> assertProblem(as(employee, "POST", ROOMS, "{\"name\":\"\"}"), 403, "ACCESS_DENIED");
      case ADMIN_ROOM_POST_BAD_BODY -> assertProblem(as(admin, "POST", ROOMS, "{\"name\":\"\"}"), 400, "VALIDATION_FAILED");
      case MANAGER_BAD_BODY_UNKNOWN_ROOM -> {
        Map<String, Object> body = bookingBody(unknown, START, END);
        body.put("endTime", START.minusSeconds(60).toString());
        assertProblem(postJson(manager, BOOKINGS, body), 400, "VALIDATION_FAILED");
      }
      case MANAGER_APPROVE_MALFORMED_UUID -> assertProblem(patch(manager, BOOKINGS + "/not-a-uuid/approve"), 400, "VALIDATION_FAILED");
      case MANAGER_REJECT_BLANK_REASON_UNKNOWN_ID ->
          assertProblem(as(manager, "PATCH", BOOKINGS + "/" + unknown + "/reject", Map.of("reason", " ")), 400, "VALIDATION_FAILED");
      case MANAGER_APPROVE_UNKNOWN_ID -> assertProblem(patch(manager, BOOKINGS + "/" + unknown + "/approve"), 404, "BOOKING_NOT_FOUND");
      case EMPLOYEE_CANCEL_UNKNOWN_ID -> assertProblem(patch(employee, BOOKINGS + "/" + unknown + "/cancel"), 404, "BOOKING_NOT_FOUND");
      case NON_OWNER_ON_TERMINAL -> {
        Booking b = terminal(employee, BookingStatus.CANCELLED);
        assertProblem(patch(other, BOOKINGS + "/" + b.getId() + "/cancel"), 403, "ACCESS_DENIED");
      }
      case OWNER_ON_TERMINAL -> {
        Booking b = terminal(employee, BookingStatus.CANCELLED);
        assertProblem(patch(employee, BOOKINGS + "/" + b.getId() + "/cancel"), 409, "INVALID_STATE_TRANSITION");
      }
      case OWNER_CHECKIN_TERMINAL_OUTSIDE_WINDOW -> {
        Booking b = terminal(employee, BookingStatus.CANCELLED);
        clock.set(START.plus(5, ChronoUnit.HOURS)); // far outside the check-in window
        assertProblem(patch(employee, BOOKINGS + "/" + b.getId() + "/check-in"), 409, "INVALID_STATE_TRANSITION");
      }
      case OWNER_CANCEL_COMPLETED_AFTER_START -> {
        Booking b = terminal(employee, BookingStatus.COMPLETED);
        clock.set(START.plus(5, ChronoUnit.MINUTES));
        assertProblem(patch(employee, BOOKINGS + "/" + b.getId() + "/cancel"), 409, "INVALID_STATE_TRANSITION");
      }
      case OWNER_CANCEL_CHECKED_IN_AFTER_START -> {
        Booking b = terminal(employee, BookingStatus.CHECKED_IN);
        clock.set(START.plus(5, ChronoUnit.MINUTES));
        assertProblem(patch(employee, BOOKINGS + "/" + b.getId() + "/cancel"), 409, "INVALID_STATE_TRANSITION");
      }
      case EMPLOYEE_INCLUDE_INACTIVE_BAD_CAPACITY ->
          assertProblem(get(employee, ROOMS + "?includeInactive=true&minCapacity=abc"), 403, "ACCESS_DENIED");
      case ADMIN_INCLUDE_INACTIVE_BAD_CAPACITY ->
          assertProblem(get(admin, ROOMS + "?includeInactive=true&minCapacity=abc"), 400, "VALIDATION_FAILED");
    }
    assertThat(c).isNotNull();
  }
}
