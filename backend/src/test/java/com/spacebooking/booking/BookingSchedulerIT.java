package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.service.BookingScheduler;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/** R19-R21 at exact boundaries on a real DB, R23 (domain.md sections 4, 5, 8, 10). */
class BookingSchedulerIT extends AbstractDomainIT {

  /** 12:00 in Asia/Ho_Chi_Minh, far from any other test's data and from a day boundary. */
  private static final Instant BASE = Instant.parse("2029-06-15T05:00:00Z");

  private static final LocalDate BASE_DAY = LocalDate.of(2029, 6, 15);

  @Autowired private BookingScheduler scheduler;

  /** Runs the job at BASE. The clock is restored before any HTTP call: tokens are validated against it too. */
  private void runAtBase() {
    clock.set(BASE);
    try {
      scheduler.run();
    } finally {
      clock.reset();
    }
  }

  private static Instant at(long minutesFromBase) {
    return BASE.plus(minutesFromBase, ChronoUnit.MINUTES);
  }

  private Booking seed(BookingStatus status, long startMin, long endMin) {
    return booking(room(false), actor(Role.EMPLOYEE), at(startMin), at(endMin), status);
  }

  static List<Arguments> boundaries() {
    return List.of(
        // status, start offset, end offset (minutes from now), expected status, expected audit action (null = untouched)
        Arguments.of("PENDING at start", BookingStatus.PENDING, 0, 60, "CANCELLED", "EXPIRED"),
        Arguments.of("PENDING 1 min before start", BookingStatus.PENDING, 1, 61, "PENDING", null),
        Arguments.of("CONFIRMED at start+15m", BookingStatus.CONFIRMED, -15, 45, "CONFIRMED", null),
        Arguments.of("CONFIRMED at start+16m", BookingStatus.CONFIRMED, -16, 44, "NO_SHOW", "NO_SHOW"),
        Arguments.of("CHECKED_IN at end", BookingStatus.CHECKED_IN, -60, 0, "COMPLETED", "COMPLETED"),
        Arguments.of("CHECKED_IN 1 min before end", BookingStatus.CHECKED_IN, -59, 1, "CHECKED_IN", null));
  }

  // R19, R20, R21
  @ParameterizedTest(name = "{0}")
  @MethodSource("boundaries")
  void boundary(String label, BookingStatus status, int startMin, int endMin, String expectedStatus, String expectedAudit) {
    Booking b = seed(status, startMin, endMin);

    runAtBase();

    assertThat(statusOf(b.getId())).isEqualTo(expectedStatus);
    List<Map<String, Object>> audit = auditRows(b.getId());
    if (expectedAudit == null) {
      assertThat(audit).as("untouched booking has no audit row").isEmpty();
    } else {
      assertThat(audit).as("one audit row per transition").hasSize(1);
      assertThat(audit.get(0).get("action")).isEqualTo(expectedAudit);
      assertThat(audit.get(0).get("performed_by")).isEqualTo("SYSTEM_SCHEDULER");
    }
  }

  // R23
  @Test
  void transition_writesAuditAndEvicts() throws Exception {
    Actor viewer = actor(Role.EMPLOYEE);
    Booking pending = seed(BookingStatus.PENDING, 0, 60);
    Booking confirmed = seed(BookingStatus.CONFIRMED, -16, 44);
    Booking checkedIn = seed(BookingStatus.CHECKED_IN, -60, 0);
    Booking untouched = seed(BookingStatus.CONFIRMED, 30, 90);
    List<UUID> moving = List.of(pending.getId(), confirmed.getId(), checkedIn.getId());

    assertThat(scheduleStatuses(viewer, pending.getRoom(), confirmed.getRoom(), checkedIn.getRoom(), untouched.getRoom()))
        .as("warm read before the run")
        .containsOnlyKeys(pending.getId(), confirmed.getId(), checkedIn.getId(), untouched.getId());
    assertThat(cacheManager.getCache("dailySchedule").get(BASE_DAY)).as("schedule is cached").isNotNull();

    runAtBase();

    assertThat(cacheManager.getCache("dailySchedule").get(BASE_DAY)).as("evicted after commit").isNull();
    Map<UUID, String> after =
        scheduleStatuses(viewer, pending.getRoom(), confirmed.getRoom(), checkedIn.getRoom(), untouched.getRoom());
    assertThat(after).containsOnlyKeys(untouched.getId());
    assertThat(after.get(untouched.getId())).isEqualTo("CONFIRMED");
    for (UUID id : moving) {
      List<Map<String, Object>> audit = auditRows(id);
      assertThat(audit).hasSize(1);
      assertThat(audit.get(0).get("performed_by")).isEqualTo("SYSTEM_SCHEDULER");
      JsonNode details = mapper.readTree((String) audit.get(0).get("details"));
      assertThat(details.hasNonNull("from")).isTrue();
      assertThat(details.hasNonNull("to")).isTrue();
    }
    assertThat(auditRows(untouched.getId())).isEmpty();
  }

  /** booking id -> status for the bookings of the given rooms on BASE_DAY, read through HTTP (cached path). */
  private Map<UUID, String> scheduleStatuses(Actor viewer, Room... rooms) {
    Resp r = get(viewer, BOOKINGS + "/daily-schedule?date=" + BASE_DAY);
    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    List<String> mine = new ArrayList<>();
    for (Room room : rooms) {
      mine.add(room.getId().toString());
    }
    Map<UUID, String> out = new java.util.LinkedHashMap<>();
    r.body()
        .path("rooms")
        .forEach(
            n -> {
              if (mine.contains(n.path("room").path("id").asText())) {
                n.path("bookings").forEach(b -> out.put(UUID.fromString(b.path("id").asText()), b.path("status").asText()));
              }
            });
    return out;
  }
}
