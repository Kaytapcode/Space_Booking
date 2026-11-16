package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class BookingFlowIT extends AbstractDomainIT {

  // R18
  @Test
  void earlyCheckout_freesRemainingSlot() {
    Actor first = actor(Role.EMPLOYEE);
    Actor second = actor(Role.EMPLOYEE);
    Room room = room(false);
    Instant start = inMinutes(120);
    Instant end = start.plus(60, ChronoUnit.MINUTES);

    Resp created = postJson(first, BOOKINGS, bookingBody(room.getId(), start, end));
    assertThat(created.status()).as("body: %s", created.body()).isEqualTo(201);
    String id = created.body().path("id").asText();
    assertThat(created.body().path("status").asText()).isEqualTo("CONFIRMED");

    Instant tailStart = start.plus(21, ChronoUnit.MINUTES);
    Resp blocked = postJson(second, BOOKINGS, bookingBody(room.getId(), tailStart, end));
    assertProblem(blocked, 409, "BOOKING_CONFLICT");

    clock.set(start.plus(5, ChronoUnit.MINUTES));
    Resp in = patch(first, BOOKINGS + "/" + id + "/check-in");
    assertThat(in.status()).as("body: %s", in.body()).isEqualTo(200);
    assertThat(in.body().path("status").asText()).isEqualTo("CHECKED_IN");

    clock.set(start.plus(20, ChronoUnit.MINUTES).plusSeconds(10)); // rounds up to start+21m
    Resp out = patch(first, BOOKINGS + "/" + id + "/checkout");
    assertThat(out.status()).as("body: %s", out.body()).isEqualTo(200);
    assertThat(out.body().path("status").asText()).isEqualTo("COMPLETED");
    assertThat(Instant.parse(out.body().path("endTime").asText())).isEqualTo(tailStart);

    Resp freed = postJson(second, BOOKINGS, bookingBody(room.getId(), tailStart, end));
    assertThat(freed.status()).as("body: %s", freed.body()).isEqualTo(201);
    assertThat(freed.body().path("status").asText()).isEqualTo("CONFIRMED");
  }
}
