package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.audit.domain.AuditAction;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** R16 (domain.md section 4). */
class PendingStateTest {

  @Test
  void reject_setsReason() {
    Booking b = BookingStateTest.booking(BookingStatus.PENDING);
    TransitionInput in = new TransitionInput(BookingStateTest.START.minus(Duration.ofMinutes(5)), "Phòng đang bảo trì");

    AuditAction audit = new PendingState().apply(b, BookingAction.REJECT, in);

    assertThat(audit).isEqualTo(AuditAction.REJECTED);
    assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(b.getRejectionReason()).isEqualTo("Phòng đang bảo trì");
  }

  @Test
  void cancel_leavesReasonEmpty() {
    Booking b = BookingStateTest.booking(BookingStatus.PENDING);

    new PendingState().apply(b, BookingAction.CANCEL, new TransitionInput(BookingStateTest.START.minus(Duration.ofMinutes(5)), null));

    assertThat(b.getRejectionReason()).isNull();
  }
}
