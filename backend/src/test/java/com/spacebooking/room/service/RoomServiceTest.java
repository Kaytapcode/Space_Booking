package com.spacebooking.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** R40 service level (domain.md section 11): deactivation guard. */
class RoomServiceTest extends AbstractDomainIT {

  @Autowired RoomService roomService;

  @Test
  void deactivate_futureBlocking_409() {
    Room room = room(false);
    booking(room, actor(Role.EMPLOYEE), inMinutes(60), inMinutes(120), BookingStatus.CONFIRMED);

    assertThatThrownBy(() -> roomService.deactivate(room.getId()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ROOM_HAS_FUTURE_BOOKINGS));
    assertThat(jdbc.queryForObject("SELECT is_active FROM rooms WHERE id = ?", Boolean.class, room.getId())).isTrue();
  }

  @Test
  void deactivate_noBlocking_ok() {
    Room room = room(false);
    booking(room, actor(Role.EMPLOYEE), inMinutes(60), inMinutes(120), BookingStatus.CANCELLED);

    assertThatCode(() -> roomService.deactivate(room.getId())).doesNotThrowAnyException();
    assertThat(jdbc.queryForObject("SELECT is_active FROM rooms WHERE id = ?", Boolean.class, room.getId())).isFalse();
  }

  @Test
  void deactivate_unknown_404() {
    assertThatThrownBy(() -> roomService.deactivate(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
  }
}
