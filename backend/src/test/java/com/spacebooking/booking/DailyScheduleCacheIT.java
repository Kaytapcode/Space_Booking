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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/** R31, R32 (domain.md sections 8, 10): cache hit, eviction after commit on every mutation, warm latency. */
class DailyScheduleCacheIT extends AbstractDomainIT {

  private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired private BookingScheduler scheduler;

  private Actor viewer;
  private Actor owner;
  private Actor manager;
  private Actor admin;
  private String prefix;

  @BeforeEach
  void setUp() {
    viewer = actor(Role.EMPLOYEE);
    owner = actor(Role.EMPLOYEE);
    manager = actor(Role.MANAGER);
    admin = actor(Role.ADMIN);
    prefix = unique("DC");
  }

  private static Instant local(LocalDate day, int hour, int minute) {
    return LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(HCM).toInstant();
  }

  private Resp readSchedule(LocalDate day) {
    Resp r = get(viewer, BOOKINGS + "/daily-schedule?date=" + day);
    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    return r;
  }

  private static List<String> idsIn(Resp schedule, String roomPrefix) {
    List<String> ids = new ArrayList<>();
    schedule
        .body()
        .path("rooms")
        .forEach(
            n -> {
              if (n.path("room").path("name").asText().startsWith(roomPrefix)) {
                n.path("bookings").forEach(b -> ids.add(b.path("id").asText()));
              }
            });
    return ids;
  }

  private boolean cached(LocalDate day) {
    return cacheManager.getCache("dailySchedule").get(day) != null;
  }

  private static void assert2xx(Resp r) {
    assertThat(r.status()).as("body: %s", r.body()).isBetween(200, 299);
  }

  // R31: the second read is served from the cache, so a row written behind the service's back stays invisible
  // until a real mutation commits and evicts.
  @Test
  void secondCall_hitsCache() {
    LocalDate day = LocalDate.of(2032, 3, 10);
    Room room = room(prefix + "-A", 8, "", false);

    assertThat(idsIn(readSchedule(day), prefix)).isEmpty();
    assertThat(cached(day)).as("first call fills the cache").isTrue();

    String sneaked = booking(room, owner, local(day, 9, 0), local(day, 10, 0), BookingStatus.CONFIRMED).getId().toString();

    assertThat(idsIn(readSchedule(day), prefix)).as("second call is a cache hit: stale by design").isEmpty();

    assert2xx(postJson(admin, ROOMS, roomBody(prefix + "-B", 4, List.of(), false))); // evicts after commit

    assertThat(idsIn(readSchedule(day), prefix)).as("third call re-reads the database").containsExactly(sneaked);
  }

  /** Creates what the mutation needs and returns the mutation itself. */
  private Runnable prepare(String mutation) {
    Instant in60 = inMinutes(60);
    Instant in5 = inMinutes(5);
    return switch (mutation) {
      case "create" -> {
        Room r = room(false);
        yield () -> assert2xx(postJson(owner, BOOKINGS, bookingBody(r.getId(), in60, in60.plus(1, ChronoUnit.HOURS))));
      }
      case "approve" -> {
        Booking b = booking(room(true), owner, in60, in60.plus(1, ChronoUnit.HOURS), BookingStatus.PENDING);
        yield () -> assert2xx(patch(manager, BOOKINGS + "/" + b.getId() + "/approve"));
      }
      case "reject" -> {
        Booking b = booking(room(true), owner, in60, in60.plus(1, ChronoUnit.HOURS), BookingStatus.PENDING);
        yield () -> assert2xx(as(manager, "PATCH", BOOKINGS + "/" + b.getId() + "/reject", Map.of("reason", "Không còn phòng")));
      }
      case "cancel" -> {
        Booking b = booking(room(false), owner, in60, in60.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
        yield () -> assert2xx(patch(owner, BOOKINGS + "/" + b.getId() + "/cancel"));
      }
      case "checkIn" -> {
        Booking b = booking(room(false), owner, in5, in5.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
        yield () -> assert2xx(patch(owner, BOOKINGS + "/" + b.getId() + "/check-in"));
      }
      case "checkout" -> {
        Booking b = booking(room(false), owner, inMinutes(-10), inMinutes(50), BookingStatus.CHECKED_IN);
        yield () -> assert2xx(patch(owner, BOOKINGS + "/" + b.getId() + "/checkout"));
      }
      case "expire" -> {
        Booking b = booking(room(true), owner, in60, in60.plus(1, ChronoUnit.HOURS), BookingStatus.PENDING);
        yield () -> runSchedulerAt(in60, b);
      }
      case "noShow" -> {
        Booking b = booking(room(false), owner, in60, in60.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
        yield () -> runSchedulerAt(in60.plus(16, ChronoUnit.MINUTES), b);
      }
      case "complete" -> {
        Instant end = in60.plus(1, ChronoUnit.HOURS);
        Booking b = booking(room(false), owner, in60, end, BookingStatus.CHECKED_IN);
        yield () -> runSchedulerAt(end, b);
      }
      case "roomCreate" -> () -> assert2xx(postJson(admin, ROOMS, roomBody(prefix + "-new", 4, List.of(), false)));
      case "roomPut" -> {
        Room r = room(false);
        yield () -> assert2xx(as(admin, "PUT", ROOMS + "/" + r.getId(), roomBody(prefix + "-renamed", 9, List.of("tv"), false)));
      }
      case "roomPutActiveFalse" -> {
        Room r = room(false);
        Map<String, Object> body = roomBody(r.getName(), r.getCapacity(), List.of(), false);
        body.put("active", false);
        yield () -> assert2xx(as(admin, "PUT", ROOMS + "/" + r.getId(), body));
      }
      case "roomDelete" -> {
        Room r = room(false);
        yield () -> assert2xx(as(admin, "DELETE", ROOMS + "/" + r.getId(), null));
      }
      default -> throw new IllegalArgumentException(mutation);
    };
  }

  private void runSchedulerAt(Instant now, Booking expectedToMove) {
    clock.set(now);
    try {
      scheduler.run();
    } finally {
      clock.reset();
    }
    assertThat(statusOf(expectedToMove.getId())).isNotEqualTo(expectedToMove.getStatus().name());
  }

  // R31
  @ParameterizedTest(name = "{0} -> evictedAfter")
  @ValueSource(
      strings = {
        "create", "approve", "reject", "cancel", "checkIn", "checkout", "expire", "noShow", "complete",
        "roomCreate", "roomPut", "roomPutActiveFalse", "roomDelete"
      })
  void mutation_evictedAfter(String mutation) {
    LocalDate today = LocalDate.now(HCM);
    LocalDate other = today.plusDays(7);
    Runnable action = prepare(mutation);
    readSchedule(today);
    readSchedule(other);
    assertThat(cached(today)).as("warm before %s", mutation).isTrue();
    assertThat(cached(other)).isTrue();

    action.run();

    assertThat(cached(today)).as("%s evicts every date once committed", mutation).isFalse();
    assertThat(cached(other)).isFalse();
  }

  // R32: >= 50 bookings, warm path
  @Test
  void warmResponse_under50ms() {
    LocalDate day = LocalDate.of(2032, 4, 14);
    for (int r = 0; r < 10; r++) {
      Room room = room(prefix + "-" + r, 8, "tv", false);
      for (int h = 0; h < 6; h++) {
        booking(room, owner, local(day, 8 + h, 0), local(day, 9 + h, 0), BookingStatus.CONFIRMED);
      }
    }
    Resp cold = readSchedule(day);
    assertThat(idsIn(cold, prefix)).hasSize(60);
    assertThat(cached(day)).isTrue();

    int calls = 200;
    long[] nanos = new long[calls];
    for (int i = 0; i < calls; i++) {
      long t0 = System.nanoTime();
      Resp r = get(viewer, BOOKINGS + "/daily-schedule?date=" + day);
      nanos[i] = System.nanoTime() - t0;
      assertThat(r.status()).isEqualTo(200);
    }
    Arrays.sort(nanos);
    double p95Ms = nanos[(int) Math.ceil(calls * 0.95) - 1] / 1_000_000.0;
    System.out.printf("daily-schedule warm p95 = %.2f ms over %d calls (60 bookings)%n", p95Ms, calls);

    assertThat(p95Ms).as("warm daily-schedule p95 (ms)").isLessThan(50.0);
    JsonNode body = get(viewer, BOOKINGS + "/daily-schedule?date=" + day).body();
    assertThat(body.path("date").asText()).isEqualTo(day.toString());
  }
}
