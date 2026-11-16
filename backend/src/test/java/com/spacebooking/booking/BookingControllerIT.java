package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BookingControllerIT extends AbstractDomainIT {

  private static final Instant START = inMinutes(120);
  private static final Instant END = START.plus(60, ChronoUnit.MINUTES);

  private Resp create(Actor a, Room r, Instant start, Instant end) {
    return postJson(a, BOOKINGS, bookingBody(r.getId(), start, end));
  }

  private static List<String> ids(JsonNode content) {
    List<String> out = new ArrayList<>();
    content.forEach(n -> out.add(n.path("id").asText()));
    return out;
  }

  private static List<String> byId(List<String> ids) {
    return ids.stream().sorted().toList(); // lower-case hex: string order == Postgres uuid order
  }

  // ---------------- A12 POST /bookings ----------------

  @Test
  void create_201_confirmed() {
    Actor employee = actor(Role.EMPLOYEE);
    Room room = room(false);

    Resp r = create(employee, room, START, END);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    JsonNode b = r.body();
    assertThat(b.path("status").asText()).isEqualTo("CONFIRMED");
    assertThat(b.path("room").path("id").asText()).isEqualTo(room.getId().toString());
    assertThat(b.path("room").path("name").asText()).isEqualTo(room.getName());
    assertThat(b.path("user").path("id").asText()).isEqualTo(employee.id().toString());
    assertThat(b.path("user").has("email")).as("user summary never includes email").isFalse();
    assertThat(b.path("title").asText()).isEqualTo("Họp kế hoạch");
    assertThat(Instant.parse(b.path("startTime").asText())).isEqualTo(START);
    assertThat(Instant.parse(b.path("endTime").asText())).isEqualTo(END);
    assertThat(b.has("rejectionReason")).isTrue();
    assertThat(b.get("rejectionReason").isNull()).isTrue();
    assertThat(Instant.parse(b.path("createdAt").asText())).isNotNull();
    assertThat(statusOf(UUID.fromString(b.path("id").asText()))).isEqualTo("CONFIRMED");
  }

  @Test
  void create_201_pending() {
    Actor employee = actor(Role.EMPLOYEE);

    Resp r = create(employee, room(true), START, END);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    assertThat(r.body().path("status").asText()).isEqualTo("PENDING");
  }

  @Test
  void create_201_managerOnApprovalRoom_confirmedAndLong() {
    Actor manager = actor(Role.MANAGER);

    Resp r = create(manager, room(true), START, START.plus(8, ChronoUnit.HOURS));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    assertThat(r.body().path("status").asText()).isEqualTo("CONFIRMED");
  }

  static Stream<Arguments> invalidBodies() {
    return Stream.of(
        Arguments.of("missing title", (Consumer<Map<String, Object>>) m -> m.remove("title"), "title"),
        Arguments.of("empty title", (Consumer<Map<String, Object>>) m -> m.put("title", ""), "title"),
        Arguments.of("title 256 chars", (Consumer<Map<String, Object>>) m -> m.put("title", "x".repeat(256)), "title"),
        Arguments.of("missing roomId", (Consumer<Map<String, Object>>) m -> m.remove("roomId"), "roomId"),
        Arguments.of("roomId not a uuid", (Consumer<Map<String, Object>>) m -> m.put("roomId", "xyz"), null),
        Arguments.of("missing startTime", (Consumer<Map<String, Object>>) m -> m.remove("startTime"), "startTime"),
        Arguments.of("missing endTime", (Consumer<Map<String, Object>>) m -> m.remove("endTime"), "endTime"),
        Arguments.of("startTime not a date", (Consumer<Map<String, Object>>) m -> m.put("startTime", "tomorrow"), null),
        Arguments.of("start has seconds", (Consumer<Map<String, Object>>) m -> m.put("startTime", START.plusSeconds(30).toString()), "startTime"),
        Arguments.of("end equals start", (Consumer<Map<String, Object>>) m -> m.put("endTime", START.toString()), "endTime"),
        Arguments.of("end before start", (Consumer<Map<String, Object>>) m -> m.put("endTime", START.minusSeconds(60).toString()), "endTime"),
        Arguments.of("unknown property", (Consumer<Map<String, Object>>) m -> m.put("status", "CONFIRMED"), null));
  }

  @ParameterizedTest(name = "create_400: {0}")
  @MethodSource("invalidBodies")
  void create_400(String name, Consumer<Map<String, Object>> mutate, String field) {
    Actor employee = actor(Role.EMPLOYEE);
    Map<String, Object> body = bookingBody(room(false).getId(), START, END);
    mutate.accept(body);

    Resp r = postJson(employee, BOOKINGS, body);

    assertProblem(r, 400, "VALIDATION_FAILED");
    if (field != null) {
      List<String> fields = new ArrayList<>();
      r.body().path("errors").forEach(e -> fields.add(e.path("field").asText()));
      assertThat(fields).contains(field);
    }
  }

  @Test
  void create_malformedJson_400() {
    assertProblem(as(actor(Role.EMPLOYEE), "POST", BOOKINGS, "{not json"), 400, "VALIDATION_FAILED");
  }

  @Test
  void create_startInPast_400() {
    Actor employee = actor(Role.EMPLOYEE);

    assertProblem(create(employee, room(false), inMinutes(-5), inMinutes(30)), 400, "START_IN_PAST");
  }

  @Test
  void create_employee121min_400() {
    Actor employee = actor(Role.EMPLOYEE);
    Room room = room(false);

    assertProblem(create(employee, room, START, START.plus(121, ChronoUnit.MINUTES)), 400, "DURATION_EXCEEDED");
    assertThat(create(employee, room, START, START.plus(120, ChronoUnit.MINUTES)).status()).isEqualTo(201);
  }

  @Test
  void create_unknownRoom_404() {
    Actor employee = actor(Role.EMPLOYEE);

    assertProblem(postJson(employee, BOOKINGS, bookingBody(UUID.randomUUID(), START, END)), 404, "ROOM_NOT_FOUND");
  }

  // R39
  @Test
  void create_inactiveRoom_404() {
    Room inactive = inactiveRoom();

    assertProblem(create(actor(Role.EMPLOYEE), inactive, START, END), 404, "ROOM_NOT_FOUND");
    assertProblem(create(actor(Role.MANAGER), inactive, START, END), 404, "ROOM_NOT_FOUND");
  }

  @Test
  void create_overlap_409() {
    Actor a = actor(Role.EMPLOYEE);
    Actor b = actor(Role.EMPLOYEE);
    Room room = room(false);
    assertThat(create(a, room, START, END).status()).isEqualTo(201);

    assertProblem(create(b, room, START.plus(30, ChronoUnit.MINUTES), END.plus(30, ChronoUnit.MINUTES)), 409, "BOOKING_CONFLICT");
    assertProblem(create(b, room, START, END), 409, "BOOKING_CONFLICT");
    assertThat(blockingCount(room.getId())).isEqualTo(1);
  }

  @Test
  void create_adjacentAndAfterCancelled_201() {
    Actor a = actor(Role.EMPLOYEE);
    Room room = room(false);
    booking(room, a, START, END, BookingStatus.CANCELLED);
    assertThat(create(a, room, START, END).status()).as("cancelled never blocks").isEqualTo(201);

    assertThat(create(a, room, END, END.plus(30, ChronoUnit.MINUTES)).status()).as("adjacent after").isEqualTo(201);
    assertThat(create(a, room, START.minus(30, ChronoUnit.MINUTES), START).status()).as("adjacent before").isEqualTo(201);
  }

  @Test
  void create_admin_403() {
    assertProblem(create(actor(Role.ADMIN), room(false), START, END), 403, "ACCESS_DENIED");
  }

  // R55
  @ParameterizedTest(name = "offset {0}")
  @ValueSource(strings = {"+07:00", "-05:00", "+00:00", "Z"})
  void create_offsetInput_utcOutput(String offset) {
    Actor employee = actor(Role.EMPLOYEE);
    Room room = room(false);
    ZoneOffset zone = ZoneOffset.of(offset);
    Map<String, Object> body = bookingBody(room.getId(), START, END);
    body.put("startTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(START.atOffset(zone)));
    body.put("endTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(END, zone)));

    Resp r = postJson(employee, BOOKINGS, body);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    assertThat(r.body().path("startTime").asText()).endsWith("Z");
    assertThat(r.body().path("endTime").asText()).endsWith("Z");
    assertThat(r.body().path("createdAt").asText()).endsWith("Z");
    assertThat(Instant.parse(r.body().path("startTime").asText())).isEqualTo(START);
    assertThat(Instant.parse(r.body().path("endTime").asText())).isEqualTo(END);
  }

  // domain.md section 1: TIMESTAMP columns hold UTC, whatever the JVM default zone is
  @Test
  void create_storesUtcTimestamps() {
    Room room = room(false);
    Instant start = Instant.parse("2031-06-01T09:00:00Z");

    Resp r = create(actor(Role.EMPLOYEE), room, start, start.plusSeconds(3600));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    assertThat(jdbc.queryForObject("SELECT start_time::text FROM bookings WHERE room_id = ?", String.class, room.getId()))
        .isEqualTo("2031-06-01 09:00:00");
    assertThat(jdbc.queryForObject("SELECT end_time::text FROM bookings WHERE room_id = ?", String.class, room.getId()))
        .isEqualTo("2031-06-01 10:00:00");
  }

  // ---------------- A10 GET /bookings/my-bookings ----------------

  @Test
  void myBookings_200_onlyOwn_sortedDesc() {
    Actor me = actor(Role.EMPLOYEE);
    Actor other = actor(Role.EMPLOYEE);
    Booking3 mine = Booking3.of(this, me);
    booking(room(false), other, START, END, BookingStatus.CONFIRMED);

    Resp r = get(me, BOOKINGS + "/my-bookings");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(ids(r.body().path("content"))).containsExactly(mine.latest, mine.middle, mine.earliest);
    assertThat(r.body().path("page").asInt()).isZero();
    assertThat(r.body().path("size").asInt()).isEqualTo(20);
    assertThat(r.body().path("totalElements").asInt()).isEqualTo(3);
    assertThat(r.body().path("totalPages").asInt()).isEqualTo(1);
  }

  @Test
  void myBookings_paging() {
    Actor me = actor(Role.EMPLOYEE);
    Booking3 mine = Booking3.of(this, me);

    Resp first = get(me, BOOKINGS + "/my-bookings?page=0&size=2");
    Resp second = get(me, BOOKINGS + "/my-bookings?page=1&size=2");

    assertThat(ids(first.body().path("content"))).containsExactly(mine.latest, mine.middle);
    assertThat(ids(second.body().path("content"))).containsExactly(mine.earliest);
    assertThat(first.body().path("totalElements").asInt()).isEqualTo(3);
    assertThat(first.body().path("totalPages").asInt()).isEqualTo(2);
    assertThat(second.body().path("page").asInt()).isEqualTo(1);
    assertThat(second.body().path("size").asInt()).isEqualTo(2);
  }

  @Test
  void myBookings_statusFilter() {
    Actor me = actor(Role.MANAGER);
    booking(room(false), me, START, END, BookingStatus.CONFIRMED);
    String cancelled = booking(room(false), me, START, END, BookingStatus.CANCELLED).getId().toString();
    booking(room(false), me, START, END, BookingStatus.PENDING);

    Resp r = get(me, BOOKINGS + "/my-bookings?status=CANCELLED");

    assertThat(r.status()).isEqualTo(200);
    assertThat(ids(r.body().path("content"))).containsExactly(cancelled);
    assertThat(r.body().path("totalElements").asInt()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"size=101", "size=500", "size=0", "page=-1", "status=BOGUS", "size=abc"})
  void myBookings_badParams_400(String query) {
    assertProblem(get(actor(Role.EMPLOYEE), BOOKINGS + "/my-bookings?" + query), 400, "VALIDATION_FAILED");
  }

  @Test
  void myBookings_sizeOver100_400() {
    assertProblem(get(actor(Role.EMPLOYEE), BOOKINGS + "/my-bookings?size=101"), 400, "VALIDATION_FAILED");
    assertThat(get(actor(Role.EMPLOYEE), BOOKINGS + "/my-bookings?size=100").status()).isEqualTo(200);
  }

  // R61
  @Test
  void myBookings_equalStart_orderedById() {
    Actor me = actor(Role.EMPLOYEE);
    List<String> created = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      created.add(booking(room(false), me, START, END, BookingStatus.CONFIRMED).getId().toString());
    }

    Resp r = get(me, BOOKINGS + "/my-bookings");

    assertThat(ids(r.body().path("content"))).containsExactlyElementsOf(byId(created));
  }

  // ---------------- A11 GET /bookings/pending ----------------

  @Test
  void pending_200_sortedAsc() {
    Actor manager = actor(Role.MANAGER);
    Actor employee = actor(Role.EMPLOYEE);
    String late = booking(room(true), employee, inMinutes(300), inMinutes(360), BookingStatus.PENDING).getId().toString();
    String early = booking(room(true), employee, inMinutes(100), inMinutes(160), BookingStatus.PENDING).getId().toString();
    String middle = booking(room(true), employee, inMinutes(200), inMinutes(260), BookingStatus.PENDING).getId().toString();
    String confirmed = booking(room(true), employee, inMinutes(150), inMinutes(160), BookingStatus.CONFIRMED).getId().toString();

    Resp r = get(manager, BOOKINGS + "/pending?size=100");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    List<String> all = ids(r.body().path("content"));
    assertThat(all).contains(early, middle, late).doesNotContain(confirmed);
    assertThat(all.stream().filter(List.of(early, middle, late)::contains).toList()).containsExactly(early, middle, late);
    r.body().path("content").forEach(n -> assertThat(n.path("status").asText()).isEqualTo("PENDING"));
    List<Instant> starts = new ArrayList<>();
    r.body().path("content").forEach(n -> starts.add(Instant.parse(n.path("startTime").asText())));
    assertThat(starts).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  void pending_equalStart_orderedById() {
    Actor manager = actor(Role.MANAGER);
    Actor employee = actor(Role.EMPLOYEE);
    Instant start = inMinutes(5000);
    List<String> created = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      created.add(booking(room(true), employee, start, start.plusSeconds(3600), BookingStatus.PENDING).getId().toString());
    }

    Resp r = get(manager, BOOKINGS + "/pending?size=100");

    assertThat(ids(r.body().path("content")).stream().filter(created::contains).toList())
        .containsExactlyElementsOf(byId(created));
  }

  @Test
  void pending_pendingSizeOver100_400() {
    Actor manager = actor(Role.MANAGER);

    assertProblem(get(manager, BOOKINGS + "/pending?size=101"), 400, "VALIDATION_FAILED");
    assertProblem(get(manager, BOOKINGS + "/pending?page=-1"), 400, "VALIDATION_FAILED");
  }

  // ---------------- A13 approve / reject ----------------

  private com.spacebooking.booking.domain.Booking pending(Actor owner) {
    return booking(room(true), owner, START, END, BookingStatus.PENDING);
  }

  @Test
  void approve_200() {
    Actor manager = actor(Role.MANAGER);
    UUID id = pending(actor(Role.EMPLOYEE)).getId();

    Resp r = patch(manager, BOOKINGS + "/" + id + "/approve");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("id").asText()).isEqualTo(id.toString());
    assertThat(r.body().path("status").asText()).isEqualTo("CONFIRMED");
    assertThat(statusOf(id)).isEqualTo("CONFIRMED");
  }

  @Test
  void reject_200() {
    Actor manager = actor(Role.MANAGER);
    UUID id = pending(actor(Role.EMPLOYEE)).getId();

    Resp r = as(manager, "PATCH", BOOKINGS + "/" + id + "/reject", Map.of("reason", "Phòng đang bảo trì"));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("status").asText()).isEqualTo("CANCELLED");
    assertThat(r.body().path("rejectionReason").asText()).isEqualTo("Phòng đang bảo trì");
    assertThat(statusOf(id)).isEqualTo("CANCELLED");
  }

  @ParameterizedTest(name = "reject body {0}")
  @ValueSource(strings = {"{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{}", "{\"reason\":null}", "{\"reason\":\"x\",\"extra\":1}"})
  void rejectBlankReason_400(String body) {
    Actor manager = actor(Role.MANAGER);
    UUID id = pending(actor(Role.EMPLOYEE)).getId();

    assertProblem(as(manager, "PATCH", BOOKINGS + "/" + id + "/reject", body), 400, "VALIDATION_FAILED");
    assertThat(statusOf(id)).isEqualTo("PENDING");
  }

  @Test
  void rejectReason256_400_and255_200() {
    Actor manager = actor(Role.MANAGER);
    UUID tooLong = pending(actor(Role.EMPLOYEE)).getId();
    UUID ok = pending(actor(Role.EMPLOYEE)).getId();

    assertProblem(as(manager, "PATCH", BOOKINGS + "/" + tooLong + "/reject", Map.of("reason", "x".repeat(256))), 400, "VALIDATION_FAILED");
    assertThat(as(manager, "PATCH", BOOKINGS + "/" + ok + "/reject", Map.of("reason", "x".repeat(255))).status()).isEqualTo(200);
  }

  @Test
  void approve_unknown_404() {
    Actor manager = actor(Role.MANAGER);

    assertProblem(patch(manager, BOOKINGS + "/" + UUID.randomUUID() + "/approve"), 404, "BOOKING_NOT_FOUND");
    assertProblem(as(manager, "PATCH", BOOKINGS + "/" + UUID.randomUUID() + "/reject", Map.of("reason", "x")), 404, "BOOKING_NOT_FOUND");
  }

  @Test
  void approve_notPending_409() {
    Actor manager = actor(Role.MANAGER);
    UUID confirmed = booking(room(false), actor(Role.EMPLOYEE), START, END, BookingStatus.CONFIRMED).getId();

    assertProblem(patch(manager, BOOKINGS + "/" + confirmed + "/approve"), 409, "INVALID_STATE_TRANSITION");
    assertProblem(as(manager, "PATCH", BOOKINGS + "/" + confirmed + "/reject", Map.of("reason", "x")), 409, "INVALID_STATE_TRANSITION");
    assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
  }

  @Test
  void approve_afterStart_409AlreadyStarted() {
    Actor manager = actor(Role.MANAGER);
    UUID id = pending(actor(Role.EMPLOYEE)).getId();
    clock.set(START);

    assertProblem(patch(manager, BOOKINGS + "/" + id + "/approve"), 409, "BOOKING_ALREADY_STARTED");
    assertProblem(as(manager, "PATCH", BOOKINGS + "/" + id + "/reject", Map.of("reason", "x")), 409, "BOOKING_ALREADY_STARTED");
    assertThat(statusOf(id)).isEqualTo("PENDING");
  }

  // ---------------- A14 check-in / checkout / cancel ----------------

  @Test
  void checkIn_200() {
    Actor owner = actor(Role.EMPLOYEE);
    UUID id = booking(room(false), owner, START, END, BookingStatus.CONFIRMED).getId();
    clock.set(START.minus(10, ChronoUnit.MINUTES));

    Resp r = patch(owner, BOOKINGS + "/" + id + "/check-in");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("status").asText()).isEqualTo("CHECKED_IN");
    assertThat(statusOf(id)).isEqualTo("CHECKED_IN");
  }

  @Test
  void checkout_200() {
    Actor owner = actor(Role.MANAGER);
    UUID id = booking(room(false), owner, START, END, BookingStatus.CHECKED_IN).getId();
    clock.set(START.plus(30, ChronoUnit.MINUTES).plusSeconds(5));

    Resp r = patch(owner, BOOKINGS + "/" + id + "/checkout");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("status").asText()).isEqualTo("COMPLETED");
    assertThat(Instant.parse(r.body().path("endTime").asText())).isEqualTo(START.plus(31, ChronoUnit.MINUTES));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "CONFIRMED"})
  void cancel_200(BookingStatus status) {
    Actor owner = actor(Role.EMPLOYEE);
    UUID id = booking(room(true), owner, START, END, status).getId();

    Resp r = patch(owner, BOOKINGS + "/" + id + "/cancel");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("status").asText()).isEqualTo("CANCELLED");
    assertThat(r.body().path("rejectionReason").isNull()).isTrue();
  }

  @ParameterizedTest(name = "check-in at start {0} min -> 409 OUTSIDE_CHECKIN_WINDOW")
  @ValueSource(longs = {-16, 16, 120})
  void checkIn_outsideWindow_409(long minutesFromStart) {
    Actor owner = actor(Role.EMPLOYEE);
    UUID id = booking(room(false), owner, START, END, BookingStatus.CONFIRMED).getId();
    clock.set(START.plus(minutesFromStart, ChronoUnit.MINUTES));

    assertProblem(patch(owner, BOOKINGS + "/" + id + "/check-in"), 409, "OUTSIDE_CHECKIN_WINDOW");
    assertThat(statusOf(id)).isEqualTo("CONFIRMED");
  }

  @Test
  void cancelAfterStart_409AlreadyStarted() {
    Actor owner = actor(Role.EMPLOYEE);
    UUID id = booking(room(false), owner, START, END, BookingStatus.CONFIRMED).getId();
    clock.set(START.plus(1, ChronoUnit.MINUTES));

    assertProblem(patch(owner, BOOKINGS + "/" + id + "/cancel"), 409, "BOOKING_ALREADY_STARTED");
    assertThat(statusOf(id)).isEqualTo("CONFIRMED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"check-in", "checkout", "cancel"})
  void ownerActions_unknown_404(String action) {
    assertProblem(patch(actor(Role.EMPLOYEE), BOOKINGS + "/" + UUID.randomUUID() + "/" + action), 404, "BOOKING_NOT_FOUND");
  }

  @Test
  void checkout_notCheckedIn_409() {
    Actor owner = actor(Role.EMPLOYEE);
    UUID id = booking(room(false), owner, START, END, BookingStatus.CONFIRMED).getId();

    assertProblem(patch(owner, BOOKINGS + "/" + id + "/checkout"), 409, "INVALID_STATE_TRANSITION");
  }

  /** Three bookings of one user, in separate rooms, with distinct starts. */
  private record Booking3(String earliest, String middle, String latest) {
    static Booking3 of(BookingControllerIT t, Actor owner) {
      return new Booking3(
          t.booking(t.room(false), owner, inMinutes(100), inMinutes(160), BookingStatus.CONFIRMED).getId().toString(),
          t.booking(t.room(false), owner, inMinutes(200), inMinutes(260), BookingStatus.PENDING).getId().toString(),
          t.booking(t.room(false), owner, inMinutes(300), inMinutes(360), BookingStatus.CANCELLED).getId().toString());
    }
  }
}
