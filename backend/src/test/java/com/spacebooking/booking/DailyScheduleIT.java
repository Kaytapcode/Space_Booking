package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A09, R34, R35 (domain.md sections 1, 2, 10). Business day = Asia/Ho_Chi_Minh. */
class DailyScheduleIT extends AbstractDomainIT {

  private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final AtomicInteger SEQ = new AtomicInteger();

  /** Unique per test (10 days apart): rows are inserted directly, so a cached schedule of a reused date would be stale. */
  private LocalDate DAY;

  private Actor viewer;
  private Actor owner;
  private String prefix;

  @BeforeEach
  void setUp() {
    DAY = LocalDate.of(2031, 1, 10).plusDays(SEQ.getAndIncrement() * 10L);
    viewer = actor(Role.EMPLOYEE);
    owner = actor(Role.EMPLOYEE);
    prefix = unique("DS");
  }

  private static Instant local(LocalDate day, int hour, int minute) {
    return LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(HCM).toInstant();
  }

  /** Rooms of this test only (the schedule lists every active room of the shared database). */
  private List<JsonNode> mine(LocalDate day) {
    Resp r = get(viewer, BOOKINGS + "/daily-schedule?date=" + day);
    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("date").asText()).isEqualTo(day.toString());
    List<JsonNode> out = new ArrayList<>();
    r.body().path("rooms").forEach(n -> {
      if (n.path("room").path("name").asText().startsWith(prefix)) {
        out.add(n);
      }
    });
    return out;
  }

  private static List<String> bookingIds(JsonNode roomNode) {
    List<String> ids = new ArrayList<>();
    roomNode.path("bookings").forEach(b -> ids.add(b.path("id").asText()));
    return ids;
  }

  private List<String> allBookingIds(LocalDate day) {
    List<String> ids = new ArrayList<>();
    mine(day).forEach(n -> ids.addAll(bookingIds(n)));
    return ids;
  }

  private Room room(String suffix) {
    return room(prefix + "-" + suffix, 8, "tv", false);
  }

  // A09
  @Test
  void get_200_sortedRoomsAndBookings() {
    Room c = room("C");
    Room a = room("A");
    Room b = room("B");
    Room inactive = room("I");
    jdbc.update("UPDATE rooms SET is_active = FALSE WHERE id = ?", inactive.getId());
    String late = booking(a, owner, local(DAY, 14, 0), local(DAY, 15, 0), BookingStatus.CONFIRMED).getId().toString();
    List<String> tie = new ArrayList<>();
    tie.add(booking(a, owner, local(DAY, 9, 0), local(DAY, 10, 0), BookingStatus.CONFIRMED).getId().toString());
    tie.add(booking(c, owner, local(DAY, 9, 0), local(DAY, 10, 0), BookingStatus.PENDING).getId().toString());
    booking(inactive, owner, local(DAY, 9, 0), local(DAY, 10, 0), BookingStatus.CONFIRMED);

    Resp r = get(viewer, BOOKINGS + "/daily-schedule?date=" + DAY);
    List<JsonNode> rooms = mine(DAY);

    assertThat(r.status()).isEqualTo(200);
    assertThat(rooms).extracting(n -> n.path("room").path("name").asText()).containsExactly(prefix + "-A", prefix + "-B", prefix + "-C");
    JsonNode roomA = rooms.get(0);
    assertThat(roomA.path("room").path("id").asText()).isEqualTo(a.getId().toString());
    assertThat(roomA.path("room").path("capacity").asInt()).isEqualTo(8);
    assertThat(roomA.path("room").path("equipments").get(0).asText()).isEqualTo("tv");
    assertThat(roomA.path("room").path("active").asBoolean()).isTrue();
    assertThat(bookingIds(roomA)).hasSize(2).endsWith(late);
    assertThat(bookingIds(roomA).get(0)).isEqualTo(tie.get(0));
    assertThat(bookingIds(rooms.get(1))).as("room without bookings is still listed").isEmpty();
    assertThat(bookingIds(rooms.get(2))).containsExactly(tie.get(1));
    JsonNode first = roomA.path("bookings").get(0);
    assertThat(first.path("title").asText()).startsWith("Họp");
    assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
    assertThat(Instant.parse(first.path("startTime").asText())).isEqualTo(local(DAY, 9, 0));
    assertThat(first.path("startTime").asText()).endsWith("Z");
    assertThat(first.path("user").path("id").asText()).isEqualTo(owner.id().toString());
    assertThat(first.path("user").path("fullName").asText()).isEqualTo(owner.user().getFullName());
  }

  // R34
  @Test
  void bookingAt2330Local_onlyThatDay() {
    Room room = room("A");
    String id = booking(room, owner, local(DAY, 23, 0), local(DAY, 23, 59), BookingStatus.CONFIRMED).getId().toString();

    assertThat(allBookingIds(DAY)).containsExactly(id);
    assertThat(allBookingIds(DAY.plusDays(1))).isEmpty();
    assertThat(allBookingIds(DAY.minusDays(1))).isEmpty();
  }

  // R34: 00:30 local is still the previous day in UTC
  @Test
  void bookingAt0030Local_onlyThatDay() {
    Room room = room("A");
    String id = booking(room, owner, local(DAY, 0, 30), local(DAY, 1, 30), BookingStatus.CONFIRMED).getId().toString();

    assertThat(allBookingIds(DAY)).containsExactly(id);
    assertThat(allBookingIds(DAY.minusDays(1))).isEmpty();
    assertThat(allBookingIds(DAY.plusDays(1))).isEmpty();
  }

  // R34
  @Test
  void spanningMidnight_bothDays() {
    Room room = room("A");
    String id = booking(room, owner, local(DAY, 23, 0), local(DAY.plusDays(1), 1, 0), BookingStatus.CONFIRMED).getId().toString();

    assertThat(allBookingIds(DAY)).containsExactly(id);
    assertThat(allBookingIds(DAY.plusDays(1))).containsExactly(id);
    assertThat(allBookingIds(DAY.minusDays(1))).isEmpty();
    assertThat(allBookingIds(DAY.plusDays(2))).isEmpty();
  }

  @Test
  void bookingTouchingMidnight_notInNeighbourDay() {
    Room room = room("A");
    String ends = booking(room, owner, local(DAY, 22, 0), local(DAY.plusDays(1), 0, 0), BookingStatus.CONFIRMED).getId().toString();
    String starts = booking(room, owner, local(DAY.plusDays(1), 0, 0), local(DAY.plusDays(1), 1, 0), BookingStatus.CONFIRMED).getId().toString();

    assertThat(allBookingIds(DAY)).containsExactly(ends);
    assertThat(allBookingIds(DAY.plusDays(1))).containsExactly(starts);
  }

  // R35
  @Test
  void excludesNonBlocking_andHidesEmail() {
    List<String> blocking = new ArrayList<>();
    for (BookingStatus status : BookingStatus.values()) {
      Room room = room(status.name());
      Booking b = booking(room, owner, local(DAY, 9, 0), local(DAY, 10, 0), status);
      if (List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN).contains(status)) {
        blocking.add(b.getId().toString());
      }
    }

    Resp r = get(viewer, BOOKINGS + "/daily-schedule?date=" + DAY);

    List<String> shown = allBookingIds(DAY);
    assertThat(shown).containsExactlyInAnyOrderElementsOf(blocking);
    mine(DAY).forEach(n -> n.path("bookings").forEach(b -> {
      assertThat(b.path("status").asText()).isIn("PENDING", "CONFIRMED", "CHECKED_IN");
      assertThat(b.path("user").has("email")).isFalse();
      assertThat(b.path("user").size()).isEqualTo(2);
    }));
    assertThat(r.body().toString()).doesNotContain(owner.email());
  }

  // A09
  @ParameterizedTest(name = "date={0} -> 400")
  @ValueSource(strings = {"17-11-2026", "2031-02-30", "2031-13-01", "abc", "20310501", "2031-05-01T00:00:00Z"})
  void badDate_400(String date) {
    assertProblem(get(viewer, BOOKINGS + "/daily-schedule?date=" + date), 400, "VALIDATION_FAILED");
  }

  @Test
  void missingDate_400() {
    assertProblem(get(viewer, BOOKINGS + "/daily-schedule"), 400, "VALIDATION_FAILED");
    assertProblem(get(viewer, BOOKINGS + "/daily-schedule?date="), 400, "VALIDATION_FAILED");
  }
}
