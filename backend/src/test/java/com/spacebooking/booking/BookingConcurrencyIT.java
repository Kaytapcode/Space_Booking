package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** R24, R25 (domain.md section 7, creation protocol). */
class BookingConcurrencyIT extends AbstractDomainIT {

  private static final int THREADS = 12;
  private static final Instant START = inMinutes(180);
  private static final Instant END = START.plus(60, ChronoUnit.MINUTES);

  // R24
  @Test
  void parallelCreate_oneCreated_restConflict() {
    Room room = room(false);
    List<Actor> actors = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      actors.add(actor(Role.EMPLOYEE));
    }
    List<Callable<Resp>> tasks = new ArrayList<>();
    for (Actor a : actors) {
      tasks.add(() -> postJson(a, BOOKINGS, bookingBody(room.getId(), START, END)));
    }

    List<Resp> results = parallel(tasks);

    List<Resp> created = results.stream().filter(r -> r.status() == 201).toList();
    List<Resp> rest = results.stream().filter(r -> r.status() != 201).toList();
    assertThat(created).as("exactly one 201; statuses: %s", results.stream().map(Resp::status).toList()).hasSize(1);
    assertThat(rest).hasSize(THREADS - 1);
    rest.forEach(r -> assertProblem(r, 409, "BOOKING_CONFLICT"));
    assertThat(blockingCount(room.getId())).as("exactly one active row in the DB").isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs a JOIN bookings b ON b.id = a.booking_id WHERE b.room_id = ?",
                Integer.class,
                room.getId()))
        .as("one audit row, for the winner only")
        .isEqualTo(1);
  }

  @Test
  void parallelCreate_differentRooms_bothCreated() {
    Room roomA = room(false);
    Room roomB = room(false);
    Actor a = actor(Role.EMPLOYEE);
    Actor b = actor(Role.EMPLOYEE);

    List<Resp> results =
        parallel(
            List.<Callable<Resp>>of(
                () -> postJson(a, BOOKINGS, bookingBody(roomA.getId(), START, END)),
                () -> postJson(b, BOOKINGS, bookingBody(roomB.getId(), START, END))));

    results.forEach(r -> assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201));
    assertThat(blockingCount(roomA.getId())).isEqualTo(1);
    assertThat(blockingCount(roomB.getId())).isEqualTo(1);
  }

  // R25: a committed room version bump between the room load and the commit must surface as BOOKING_CONFLICT
  @Test
  void optimisticLockFailure_mapsTo409() throws Exception {
    Room room = room(false);
    Actor employee = actor(Role.EMPLOYEE);

    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        s.executeUpdate("UPDATE rooms SET version = version + 1 WHERE id = '" + room.getId() + "'");
      }
      CompletableFuture<Resp> request =
          CompletableFuture.supplyAsync(() -> postJson(employee, BOOKINGS, bookingBody(room.getId(), START, END)));
      awaitBlockedOnLock(request);
      c.commit();

      assertProblem(request.get(), 409, "BOOKING_CONFLICT");
    }

    assertThat(blockingCount(room.getId())).isZero();
  }
}
