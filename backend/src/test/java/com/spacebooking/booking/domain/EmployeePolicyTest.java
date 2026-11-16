package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.room.domain.Room;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** R05, R06 (domain.md section 3). */
class EmployeePolicyTest {

  private static final Instant START = Instant.parse("2026-11-17T02:00:00Z");
  private final EmployeePolicy policy = new EmployeePolicy();

  private static Room room(boolean requiresApproval) {
    return new Room("Phòng", "Tầng 1", 6, "", requiresApproval);
  }

  // R05
  @ParameterizedTest(name = "{0} minutes is allowed")
  @ValueSource(ints = {1, 60, 119, 120})
  void duration_withinLimit_ok(int minutes) {
    assertThat(policy.initialStatus(room(false), START, START.plusSeconds(minutes * 60L))).isNotNull();
  }

  // R05
  @ParameterizedTest(name = "{0} minutes -> DURATION_EXCEEDED")
  @ValueSource(ints = {121, 180, 1440})
  void duration_overLimit_durationExceeded(int minutes) {
    assertThatThrownBy(() -> policy.initialStatus(room(false), START, START.plusSeconds(minutes * 60L)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DURATION_EXCEEDED));
  }

  // R06
  @Test
  void initialStatus_pendingWhenRequiresApproval() {
    assertThat(policy.initialStatus(room(true), START, START.plusSeconds(3600))).isEqualTo(BookingStatus.PENDING);
  }

  // R06
  @Test
  void initialStatus_confirmedOtherwise() {
    assertThat(policy.initialStatus(room(false), START, START.plusSeconds(3600))).isEqualTo(BookingStatus.CONFIRMED);
  }

  @Test
  void durationChecked_evenWhenApprovalRoom() {
    assertThatThrownBy(() -> policy.initialStatus(room(true), START, START.plusSeconds(121 * 60L)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DURATION_EXCEEDED));
  }
}
