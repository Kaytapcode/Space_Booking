package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.room.domain.Room;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** R12, R13, R14 (domain.md section 4). The booking slot is 09:00-10:00 UTC. */
class BookingStateTest {

  static final Instant START = Instant.parse("2026-11-17T09:00:00Z");
  static final Instant END = Instant.parse("2026-11-17T10:00:00Z");
  private static final Duration MIN = Duration.ofMinutes(1);

  static Booking booking(BookingStatus status) {
    Room room = new Room("Phòng", "Tầng 1", 6, "", false);
    User user = new User("an@test.local", "hash", "An", Role.EMPLOYEE, Clock.systemUTC());
    return new Booking(
        room,
        user,
        "Họp",
        LocalDateTime.ofInstant(START, ZoneOffset.UTC),
        LocalDateTime.ofInstant(END, ZoneOffset.UTC),
        status,
        Clock.systemUTC());
  }

  private static String reasonFor(BookingAction action) {
    return action == BookingAction.REJECT ? "Phòng đang bảo trì" : null;
  }

  /** One row per section 4 transition: from, action, now, to, audit action. */
  static Stream<Arguments> validTransitions() {
    return Stream.of(
        Arguments.of(BookingStatus.PENDING, BookingAction.APPROVE, START.minus(MIN), BookingStatus.CONFIRMED, AuditAction.APPROVED),
        Arguments.of(BookingStatus.PENDING, BookingAction.REJECT, START.minus(MIN), BookingStatus.CANCELLED, AuditAction.REJECTED),
        Arguments.of(BookingStatus.PENDING, BookingAction.CANCEL, START.minus(MIN), BookingStatus.CANCELLED, AuditAction.CANCELLED),
        Arguments.of(BookingStatus.PENDING, BookingAction.EXPIRE, START, BookingStatus.CANCELLED, AuditAction.EXPIRED),
        Arguments.of(BookingStatus.PENDING, BookingAction.EXPIRE, START.plus(Duration.ofMinutes(30)), BookingStatus.CANCELLED, AuditAction.EXPIRED),
        Arguments.of(BookingStatus.CONFIRMED, BookingAction.CHECK_IN, START, BookingStatus.CHECKED_IN, AuditAction.CHECKED_IN),
        Arguments.of(BookingStatus.CONFIRMED, BookingAction.CANCEL, START.minus(MIN), BookingStatus.CANCELLED, AuditAction.CANCELLED),
        Arguments.of(BookingStatus.CONFIRMED, BookingAction.MARK_NO_SHOW, START.plus(Duration.ofMinutes(16)), BookingStatus.NO_SHOW, AuditAction.NO_SHOW),
        Arguments.of(BookingStatus.CONFIRMED, BookingAction.MARK_NO_SHOW, START.plus(Duration.ofMinutes(15)).plusSeconds(1), BookingStatus.NO_SHOW, AuditAction.NO_SHOW),
        Arguments.of(BookingStatus.CHECKED_IN, BookingAction.CHECKOUT, START.plus(Duration.ofMinutes(30)), BookingStatus.COMPLETED, AuditAction.CHECKED_OUT),
        Arguments.of(BookingStatus.CHECKED_IN, BookingAction.COMPLETE, END, BookingStatus.COMPLETED, AuditAction.COMPLETED),
        Arguments.of(BookingStatus.CHECKED_IN, BookingAction.COMPLETE, END.plus(Duration.ofHours(1)), BookingStatus.COMPLETED, AuditAction.COMPLETED));
  }

  // R12
  @ParameterizedTest(name = "{0} --{1}--> {3} (audit {4})")
  @MethodSource("validTransitions")
  void validTransition(BookingStatus from, BookingAction action, Instant now, BookingStatus to, AuditAction audit) {
    Booking b = booking(from);

    AuditAction result = BookingStates.of(from).apply(b, action, new TransitionInput(now, reasonFor(action)));

    assertThat(result).isEqualTo(audit);
    assertThat(b.getStatus()).isEqualTo(to);
  }

  private static final Set<String> VALID_PAIRS =
      Set.of(
          "PENDING:APPROVE", "PENDING:REJECT", "PENDING:CANCEL", "PENDING:EXPIRE",
          "CONFIRMED:CHECK_IN", "CONFIRMED:CANCEL", "CONFIRMED:MARK_NO_SHOW",
          "CHECKED_IN:CHECKOUT", "CHECKED_IN:COMPLETE");

  /** Every (status, action) pair that is not in section 4, each at a time before start and after end. */
  static Stream<Arguments> invalidPairs() {
    Stream.Builder<Arguments> out = Stream.builder();
    for (BookingStatus status : EnumSet.allOf(BookingStatus.class)) {
      for (BookingAction action : EnumSet.allOf(BookingAction.class)) {
        if (VALID_PAIRS.contains(status + ":" + action)) {
          continue;
        }
        out.add(Arguments.of(status, action, START.minus(Duration.ofHours(1))));
        out.add(Arguments.of(status, action, END.plus(Duration.ofHours(1))));
      }
    }
    return out.build();
  }

  // R13
  @ParameterizedTest(name = "{0} + {1} at {2} -> INVALID_STATE_TRANSITION")
  @MethodSource("invalidPairs")
  void invalidTransition(BookingStatus status, BookingAction action, Instant now) {
    Booking b = booking(status);

    assertThatThrownBy(() -> BookingStates.of(status).apply(b, action, new TransitionInput(now, reasonFor(action))))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    assertThat(b.getStatus()).as("a rejected action must not change the booking").isEqualTo(status);
    assertThat(b.getRejectionReason()).isNull();
  }

  static Stream<Arguments> afterStart() {
    Stream.Builder<Arguments> out = Stream.builder();
    for (Instant now : new Instant[] {START, START.plus(Duration.ofHours(2))}) {
      out.add(Arguments.of(BookingStatus.PENDING, BookingAction.APPROVE, now));
      out.add(Arguments.of(BookingStatus.PENDING, BookingAction.REJECT, now));
      out.add(Arguments.of(BookingStatus.PENDING, BookingAction.CANCEL, now));
      out.add(Arguments.of(BookingStatus.CONFIRMED, BookingAction.CANCEL, now));
    }
    return out.build();
  }

  // R14: now < start is required, so now == start is already too late
  @ParameterizedTest(name = "{0} + {1} at {2} -> BOOKING_ALREADY_STARTED")
  @MethodSource("afterStart")
  void afterStart_alreadyStarted(BookingStatus status, BookingAction action, Instant now) {
    Booking b = booking(status);

    assertThatThrownBy(() -> BookingStates.of(status).apply(b, action, new TransitionInput(now, reasonFor(action))))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOOKING_ALREADY_STARTED));
    assertThat(b.getStatus()).isEqualTo(status);
    assertThat(b.getRejectionReason()).isNull();
  }
}
