package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.audit.domain.AuditAction;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R17: end = min(end, max(start + 1m, ceilToMinute(now))) (domain.md sections 4 and 6). Slot 09:00-10:00 UTC. */
class CheckedInStateTest {

  @ParameterizedTest(name = "checkout at {0} -> end {1}")
  @CsvSource({
    // mid-slot: rounded up to the next minute
    "2026-11-17T09:30:20Z, 2026-11-17T09:31:00Z",
    "2026-11-17T09:30:00.000000001Z, 2026-11-17T09:31:00Z",
    "2026-11-17T09:59:30Z, 2026-11-17T10:00:00Z",
    // exactly on a minute: ceil keeps it
    "2026-11-17T09:30:00Z, 2026-11-17T09:30:00Z",
    // before start (clock skew): clamped to start + 1 minute
    "2026-11-17T08:00:00Z, 2026-11-17T09:01:00Z",
    "2026-11-17T09:00:00Z, 2026-11-17T09:01:00Z",
    "2026-11-17T09:00:30Z, 2026-11-17T09:01:00Z",
    // after the original end: end is kept
    "2026-11-17T10:00:00Z, 2026-11-17T10:00:00Z",
    "2026-11-17T10:30:00Z, 2026-11-17T10:00:00Z"
  })
  void checkout(String now, String expectedEnd) {
    Booking b = BookingStateTest.booking(BookingStatus.CHECKED_IN);

    AuditAction audit = new CheckedInState().apply(b, BookingAction.CHECKOUT, new TransitionInput(Instant.parse(now), null));

    assertThat(audit).isEqualTo(AuditAction.CHECKED_OUT);
    assertThat(b.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    assertThat(b.getEndTime()).isEqualTo(LocalDateTime.ofInstant(Instant.parse(expectedEnd), ZoneOffset.UTC));
    assertThat(b.getStartTime()).isEqualTo(LocalDateTime.ofInstant(BookingStateTest.START, ZoneOffset.UTC));
  }

  @ParameterizedTest(name = "complete at {0} keeps end")
  @CsvSource({"2026-11-17T10:00:00Z", "2026-11-17T11:00:00Z"})
  void complete_keepsEnd(String now) {
    Booking b = BookingStateTest.booking(BookingStatus.CHECKED_IN);

    new CheckedInState().apply(b, BookingAction.COMPLETE, new TransitionInput(Instant.parse(now), null));

    assertThat(b.getEndTime()).isEqualTo(LocalDateTime.ofInstant(BookingStateTest.END, ZoneOffset.UTC));
  }
}
