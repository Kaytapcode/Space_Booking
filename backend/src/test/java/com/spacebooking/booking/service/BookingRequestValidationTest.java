package com.spacebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.booking.api.BookingRequest;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/** R09, R10 (domain.md section 1). Runs through BookingService.create against real Postgres. */
class BookingRequestValidationTest extends AbstractDomainIT {

  private static final Instant NOW = Instant.parse("2026-11-17T02:15:30Z");

  @Autowired BookingService bookingService;

  private Room room;
  private Actor employee;

  @BeforeEach
  void setUp() {
    room = room(false);
    employee = actor(Role.EMPLOYEE);
    clock.set(NOW);
  }

  private void assertValidationFailed(BookingRequest req, String field) {
    assertThatThrownBy(() -> bookingService.create(req, employee.principal()))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.getErrors()).extracting(FieldError::field).contains(field);
            });
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE room_id = ?", Integer.class, room.getId()))
        .isZero();
  }

  static Stream<Arguments> secondsOrFractions() {
    Instant ok = Instant.parse("2026-11-17T03:00:00Z");
    return Stream.of(
        Arguments.of("start has seconds", Instant.parse("2026-11-17T03:00:30Z"), Instant.parse("2026-11-17T04:00:00Z"), "startTime"),
        Arguments.of("end has seconds", ok, Instant.parse("2026-11-17T04:00:15Z"), "endTime"),
        Arguments.of("start has nanos", Instant.parse("2026-11-17T03:00:00.001Z"), Instant.parse("2026-11-17T04:00:00Z"), "startTime"),
        Arguments.of("end has nanos", ok, Instant.parse("2026-11-17T04:00:00.000000001Z"), "endTime"));
  }

  // R09
  @ParameterizedTest(name = "{0}")
  @MethodSource("secondsOrFractions")
  void secondsNotZero_rejected(String name, Instant start, Instant end, String field) {
    assertValidationFailed(new BookingRequest(room.getId(), "Họp", start, end), field);
  }

  static Stream<Arguments> endNotAfterStart() {
    Instant start = Instant.parse("2026-11-17T03:00:00Z");
    return Stream.of(
        Arguments.of("end equals start", start, start),
        Arguments.of("end one minute before start", start, start.minusSeconds(60)),
        Arguments.of("end hours before start", start, start.minusSeconds(7200)));
  }

  // R10
  @ParameterizedTest(name = "{0}")
  @MethodSource("endNotAfterStart")
  void endNotAfterStart_rejected(String name, Instant start, Instant end) {
    assertValidationFailed(new BookingRequest(room.getId(), "Họp", start, end), "endTime");
  }
}
