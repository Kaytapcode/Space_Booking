package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.room.domain.Room;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** R07 (domain.md section 3): no duration limit, always CONFIRMED. */
class ManagerPolicyTest {

  private static final Instant START = Instant.parse("2026-11-17T02:00:00Z");
  private final ManagerPolicy policy = new ManagerPolicy();

  @Test
  void longBooking_confirmed() {
    Room room = new Room("Phòng", "Tầng 1", 6, "", false);

    assertThat(policy.initialStatus(room, START, START.plusSeconds(10 * 3600L))).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(policy.initialStatus(room, START, START.plusSeconds(7 * 24 * 3600L))).isEqualTo(BookingStatus.CONFIRMED);
  }

  @Test
  void approvalRoom_confirmed() {
    Room room = new Room("Hội trường", "Tầng 1", 40, "", true);

    assertThat(policy.initialStatus(room, START, START.plusSeconds(3600))).isEqualTo(BookingStatus.CONFIRMED);
  }
}
