package com.spacebooking.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/** R27 (domain.md section 7): room deactivation and booking creation bump the room version, so they cannot both win. */
class RoomConcurrencyIT extends AbstractDomainIT {

  private static final int ROUNDS = 8;

  @Test
  void deactivateVsCreate_noBookingOnInactiveRoom() {
    Actor admin = actor(Role.ADMIN);
    Actor employee = actor(Role.EMPLOYEE);
    Instant start = inMinutes(240);
    Instant end = start.plus(60, ChronoUnit.MINUTES);

    for (int round = 0; round < ROUNDS; round++) {
      Room room = room(false);

      List<Resp> results =
          parallel(
              List.<Callable<Resp>>of(
                  () -> as(admin, "DELETE", ROOMS + "/" + room.getId(), null),
                  () -> postJson(employee, BOOKINGS, bookingBody(room.getId(), start, end))));

      Resp delete = results.get(0);
      Resp create = results.get(1);
      String ctx = "round %d: delete=%d %s, create=%d %s".formatted(round, delete.status(), delete.body(), create.status(), create.body());
      boolean deactivated = !jdbc.queryForObject("SELECT is_active FROM rooms WHERE id = ?", Boolean.class, room.getId());
      int blocking = blockingCount(room.getId());

      // never an inactive room that holds a future blocking booking
      assertThat(deactivated && blocking > 0).as(ctx).isFalse();
      if (delete.status() == 204) {
        assertThat(deactivated).as(ctx).isTrue();
        assertThat(create.status()).as(ctx).isIn(404, 409);
        assertThat(create.body().path("code").asText()).as(ctx).isIn("ROOM_NOT_FOUND", "BOOKING_CONFLICT");
      } else {
        assertThat(create.status()).as(ctx).isEqualTo(201);
        assertThat(delete.status()).as(ctx).isEqualTo(409);
        assertThat(delete.body().path("code").asText()).as(ctx).isIn("ROOM_HAS_FUTURE_BOOKINGS", "CONCURRENT_UPDATE");
        assertThat(deactivated).as(ctx).isFalse();
        assertThat(blocking).as(ctx).isEqualTo(1);
      }
    }
  }
}
