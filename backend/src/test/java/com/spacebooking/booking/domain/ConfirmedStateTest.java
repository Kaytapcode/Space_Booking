package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R15: check-in window start-15m <= now <= start+15m, inclusive (domain.md section 4). Slot starts 09:00 UTC. */
class ConfirmedStateTest {

  // offsets in seconds from start; the +-1 s rows pin the inclusive boundary exactly
  @ParameterizedTest(name = "now = start {0} s -> allowed={1}")
  @CsvSource({
    "-960, false", // -16 min
    "-901, false", // just before the window
    "-900, true", //  -15 min
    "-60, true",
    "0, true",
    "60, true",
    "900, true", //   +15 min
    "901, false", //  just after the window
    "960, false" //   +16 min
  })
  void checkIn(long offsetSeconds, boolean allowed) {
    Booking b = BookingStateTest.booking(BookingStatus.CONFIRMED);
    Instant now = BookingStateTest.START.plusSeconds(offsetSeconds);

    if (allowed) {
      AuditAction audit = BookingStates.of(BookingStatus.CONFIRMED).apply(b, BookingAction.CHECK_IN, new TransitionInput(now, null));

      assertThat(audit).isEqualTo(AuditAction.CHECKED_IN);
      assertThat(b.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
    } else {
      assertThatThrownBy(() -> BookingStates.of(BookingStatus.CONFIRMED).apply(b, BookingAction.CHECK_IN, new TransitionInput(now, null)))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OUTSIDE_CHECKIN_WINDOW));
      assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
  }
}
