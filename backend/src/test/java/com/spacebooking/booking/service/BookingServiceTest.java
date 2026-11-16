package com.spacebooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.booking.api.BookingRequest;
import com.spacebooking.booking.api.BookingResponse;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** R11 (domain.md section 1): start < truncateToMinute(now) -> START_IN_PAST. now is pinned at 02:15:30Z. */
class BookingServiceTest extends AbstractDomainIT {

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

  private BookingRequest request(String start) {
    Instant s = Instant.parse(start);
    return new BookingRequest(room.getId(), "Họp", s, s.plusSeconds(3600));
  }

  @Test
  void create_startInPast_startInPast() {
    assertThatThrownBy(() -> bookingService.create(request("2026-11-17T02:14:00Z"), employee.principal()))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.START_IN_PAST));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE room_id = ?", Integer.class, room.getId()))
        .isZero();
  }

  @Test
  void create_currentMinute_ok() {
    // 02:15:00 < now (02:15:30) but equals truncateToMinute(now), so it is allowed
    BookingResponse r = bookingService.create(request("2026-11-17T02:15:00Z"), employee.principal());

    assertThat(r.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(r.startTime()).isEqualTo(Instant.parse("2026-11-17T02:15:00Z"));
  }

  @Test
  void create_nextMinute_ok() {
    BookingResponse r = bookingService.create(request("2026-11-17T02:16:00Z"), employee.principal());

    assertThat(r.status()).isEqualTo(BookingStatus.CONFIRMED);
  }
}
