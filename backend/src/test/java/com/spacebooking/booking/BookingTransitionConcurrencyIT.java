package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingAction;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.service.BookingService;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * R51 (domain.md section 7): user-vs-user pairs, user-vs-scheduler pairs and the lock timeout.
 * A losing transition waits for the row lock, sees the new status and gets 409 INVALID_STATE_TRANSITION.
 */
class BookingTransitionConcurrencyIT extends AbstractDomainIT {

  @Autowired private BookingService bookingService;

  private static final Instant START = inMinutes(120);
  private static final Instant END = START.plus(60, ChronoUnit.MINUTES);

  private void assertOneWinsOneInvalid(List<Resp> results, String bookingId) {
    List<Resp> won = results.stream().filter(r -> r.status() == 200).toList();
    List<Resp> lost = results.stream().filter(r -> r.status() != 200).toList();
    assertThat(won).as("statuses: %s", results.stream().map(Resp::status).toList()).hasSize(1);
    assertThat(lost).hasSize(1);
    assertProblem(lost.get(0), 409, "INVALID_STATE_TRANSITION");
    assertThat(won.get(0).body().path("id").asText()).isEqualTo(bookingId);
  }

  @Test
  void approveVsReject_oneWins() {
    Actor m1 = actor(Role.MANAGER);
    Actor m2 = actor(Role.MANAGER);
    Booking b = booking(room(true), actor(Role.EMPLOYEE), START, END, BookingStatus.PENDING);
    String path = BOOKINGS + "/" + b.getId();

    List<Resp> results =
        parallel(
            List.<Callable<Resp>>of(
                () -> patch(m1, path + "/approve"),
                () -> as(m2, "PATCH", path + "/reject", Map.of("reason", "Không còn phòng"))));

    assertOneWinsOneInvalid(results, b.getId().toString());
    List<Map<String, Object>> audit = auditRows(b.getId());
    assertThat(audit).as("exactly one audit row").hasSize(1);
    String status = statusOf(b.getId());
    assertThat(status).isIn("CONFIRMED", "CANCELLED");
    assertThat(audit.get(0).get("action")).isEqualTo("CONFIRMED".equals(status) ? "APPROVED" : "REJECTED");
  }

  /**
   * approve then cancel is a legal serial order (PENDING -> CONFIRMED -> CANCELLED), cancel then approve is not.
   * So the outcome is one of exactly two serialisations; audit rows equal the number of successful transitions.
   */
  @Test
  void approveVsCancel_serialisable() {
    Actor manager = actor(Role.MANAGER);
    Actor owner = actor(Role.EMPLOYEE);
    Booking b = booking(room(true), owner, START, END, BookingStatus.PENDING);
    String path = BOOKINGS + "/" + b.getId();

    List<Resp> results =
        parallel(List.<Callable<Resp>>of(() -> patch(manager, path + "/approve"), () -> patch(owner, path + "/cancel")));

    Resp approve = results.get(0);
    Resp cancel = results.get(1);
    assertThat(cancel.status()).as("cancel is legal before and after approval: %s", cancel.body()).isEqualTo(200);
    assertThat(statusOf(b.getId())).isEqualTo("CANCELLED");
    List<Map<String, Object>> audit = auditRows(b.getId());
    if (approve.status() == 200) {
      assertThat(audit).extracting(r -> r.get("action")).containsExactly("APPROVED", "CANCELLED");
    } else {
      assertProblem(approve, 409, "INVALID_STATE_TRANSITION");
      assertThat(audit).extracting(r -> r.get("action")).containsExactly("CANCELLED");
    }
  }

  @Test
  void cancelVsCancel_oneWins() {
    Actor owner = actor(Role.EMPLOYEE);
    Booking b = booking(room(false), owner, START, END, BookingStatus.CONFIRMED);
    String path = BOOKINGS + "/" + b.getId() + "/cancel";

    List<Resp> results = parallel(List.<Callable<Resp>>of(() -> patch(owner, path), () -> patch(owner, path)));

    assertOneWinsOneInvalid(results, b.getId().toString());
    assertThat(auditRows(b.getId())).hasSize(1);
    assertThat(statusOf(b.getId())).isEqualTo("CANCELLED");
  }

  @Test
  void checkInVsCancel_oneWins() {
    Actor owner = actor(Role.EMPLOYEE);
    Booking b = booking(room(false), owner, START, END, BookingStatus.CONFIRMED);
    String path = BOOKINGS + "/" + b.getId();
    clock.set(START.minus(5, ChronoUnit.MINUTES)); // inside the check-in window and before start

    List<Resp> results =
        parallel(List.<Callable<Resp>>of(() -> patch(owner, path + "/check-in"), () -> patch(owner, path + "/cancel")));

    assertOneWinsOneInvalid(results, b.getId().toString());
    List<Map<String, Object>> audit = auditRows(b.getId());
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).get("action")).isEqualTo("CHECKED_IN".equals(statusOf(b.getId())) ? "CHECKED_IN" : "CANCELLED");
  }

  // ---- user vs scheduler pairs (domain.md sections 4, 5, 7) ----

  private record Outcome(boolean won, String code) {}

  private static Outcome fromHttp(Resp r) {
    return new Outcome(r.status() == 200, r.status() == 200 ? null : r.body().path("code").asText());
  }

  /** The scheduler side of every pair: BookingScheduler.run() calls exactly this per id. */
  private Outcome fromScheduler(UUID id, BookingAction action) {
    try {
      bookingService.systemTransition(id, action);
      return new Outcome(true, null);
    } catch (ApiException e) {
      return new Outcome(false, e.getCode().name());
    }
  }

  private void assertOneWinnerOneAuditRow(List<Outcome> outcomes, Booking b, String status, String action, String performedBy) {
    assertThat(outcomes.stream().filter(Outcome::won).count()).as("outcomes: %s", outcomes).isEqualTo(1);
    assertThat(statusOf(b.getId())).isEqualTo(status);
    List<Map<String, Object>> audit = auditRows(b.getId());
    assertThat(audit).as("one audit row for the winning transition").hasSize(1);
    assertThat(audit.get(0).get("action")).isEqualTo(action);
    assertThat(audit.get(0).get("performed_by")).isEqualTo(performedBy);
  }

  // At start+15m check-in is still allowed and no-show (now > start+15m) is not, so the user wins whatever the lock order.
  @Test
  void checkInVsNoShow_oneWins() {
    Actor owner = actor(Role.EMPLOYEE);
    Booking b = booking(room(false), owner, START, END, BookingStatus.CONFIRMED);
    clock.set(START.plus(15, ChronoUnit.MINUTES));

    List<Outcome> results =
        parallel(
            List.<Callable<Outcome>>of(
                () -> fromHttp(patch(owner, BOOKINGS + "/" + b.getId() + "/check-in")),
                () -> fromScheduler(b.getId(), BookingAction.MARK_NO_SHOW)));

    assertOneWinnerOneAuditRow(results, b, "CHECKED_IN", "CHECKED_IN", owner.email());
    assertThat(results.get(1).code()).as("the scheduler side fails with an ApiException").isNotNull();
  }

  // At end both are eligible: a true race, either order is legal.
  @Test
  void checkoutVsComplete_oneWins() {
    Actor owner = actor(Role.EMPLOYEE);
    Booking b = booking(room(false), owner, START, END, BookingStatus.CHECKED_IN);
    clock.set(END);

    List<Outcome> results =
        parallel(
            List.<Callable<Outcome>>of(
                () -> fromHttp(patch(owner, BOOKINGS + "/" + b.getId() + "/checkout")),
                () -> fromScheduler(b.getId(), BookingAction.COMPLETE)));

    boolean userWon = results.get(0).won();
    assertOneWinnerOneAuditRow(
        results, b, "COMPLETED", userWon ? "CHECKED_OUT" : "COMPLETED", userWon ? owner.email() : "SYSTEM_SCHEDULER");
    assertThat(results.get(userWon ? 1 : 0).code()).isEqualTo("INVALID_STATE_TRANSITION");
  }

  // At start cancel is too late (now < start fails) and expire is due, so the scheduler wins whatever the lock order.
  @Test
  void cancelVsExpire_oneWins() {
    Actor owner = actor(Role.EMPLOYEE);
    Booking b = booking(room(true), owner, START, END, BookingStatus.PENDING);
    clock.set(START);

    List<Outcome> results =
        parallel(
            List.<Callable<Outcome>>of(
                () -> fromHttp(patch(owner, BOOKINGS + "/" + b.getId() + "/cancel")),
                () -> fromScheduler(b.getId(), BookingAction.EXPIRE)));

    assertOneWinnerOneAuditRow(results, b, "CANCELLED", "EXPIRED", "SYSTEM_SCHEDULER");
    assertThat(results.get(0).code()).isIn("BOOKING_ALREADY_STARTED", "INVALID_STATE_TRANSITION");
  }

  @Test
  void lockTimeout_409ConcurrentUpdate() throws Exception {
    Actor manager = actor(Role.MANAGER);
    Booking b = booking(room(true), actor(Role.EMPLOYEE), START, END, BookingStatus.PENDING);
    String path = BOOKINGS + "/" + b.getId() + "/approve";

    Connection holder = dataSource.getConnection();
    try {
      holder.setAutoCommit(false);
      try (PreparedStatement ps = holder.prepareStatement("SELECT id FROM bookings WHERE id = ? FOR UPDATE")) {
        ps.setObject(1, b.getId());
        ps.executeQuery();
      }
      CompletableFuture<Resp> request = CompletableFuture.supplyAsync(() -> patch(manager, path));
      awaitBlockedOnLock(request);

      Resp r;
      try {
        r = request.get(30, TimeUnit.SECONDS); // the lock is held, so only the 3 s lock timeout can answer
      } catch (TimeoutException e) {
        throw new AssertionError("approve did not give up on the held row lock within 30 s (expected ~3 s timeout)", e);
      }

      assertProblem(r, 409, "CONCURRENT_UPDATE");
    } finally {
      holder.rollback();
      holder.close();
    }

    assertThat(statusOf(b.getId())).isEqualTo("PENDING");
    assertThat(auditRows(b.getId())).isEmpty();
    assertThat(patch(manager, path).status()).as("retry after the lock is released").isEqualTo(200);
  }
}
