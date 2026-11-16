package com.spacebooking.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoomControllerIT extends AbstractDomainIT {

  private Actor admin;
  private Actor employee;
  private String prefix;

  @BeforeEach
  void setUp() {
    admin = actor(Role.ADMIN);
    employee = actor(Role.EMPLOYEE);
    prefix = unique("RC");
  }

  private Room room(String suffix, int capacity, String equipments) {
    return room(prefix + "-" + suffix, capacity, equipments, false);
  }

  private List<String> mine(Resp r) {
    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    List<String> names = new ArrayList<>();
    r.body().forEach(n -> {
      if (n.path("name").asText().startsWith(prefix + "-")) {
        names.add(n.path("name").asText().substring(prefix.length() + 1));
      }
    });
    return names;
  }

  private static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asText()));
    return out;
  }

  private String name(String suffix) {
    return prefix + "-" + suffix;
  }

  private Map<String, Object> body(String suffix) {
    return roomBody(name(suffix), 8, List.of("whiteboard", "tv"), true);
  }

  // ---------------- A04 GET /rooms ----------------

  @Test
  void search_200_sortedByName() {
    room("C", 6, "tv,whiteboard");
    room("A", 6, "");
    room("B", 6, "");
    Room inactive = room("D", 6, "");
    jdbc.update("UPDATE rooms SET is_active = FALSE WHERE id = ?", inactive.getId());

    Resp r = get(employee, ROOMS);

    assertThat(r.body().isArray()).isTrue();
    assertThat(mine(r)).containsExactly("A", "B", "C");
    JsonNode c = null;
    for (JsonNode n : r.body()) {
      if (n.path("name").asText().equals(name("C"))) {
        c = n;
      }
    }
    assertThat(c).isNotNull();
    assertThat(strings(c.path("equipments"))).containsExactly("tv", "whiteboard");
    assertThat(c.path("capacity").asInt()).isEqualTo(6);
    assertThat(c.path("location").asText()).isEqualTo("Tầng 1");
    assertThat(c.path("requiresApproval").asBoolean()).isFalse();
    assertThat(c.path("active").asBoolean()).isTrue();
    assertThat(UUID.fromString(c.path("id").asText())).isNotNull();
  }

  @Test
  void search_filters_overHttp() {
    room("A", 12, "tv,whiteboard");
    room("B", 4, "tvbox");
    room("C", 12, "tv");

    assertThat(mine(get(employee, ROOMS + "?minCapacity=6"))).containsExactly("A", "C");
    assertThat(mine(get(employee, ROOMS + "?equipments=tv"))).containsExactly("A", "C");
    assertThat(mine(get(employee, ROOMS + "?equipments=tv,whiteboard"))).containsExactly("A");
    assertThat(mine(get(employee, ROOMS + "?equipments=tvbox&minCapacity=4"))).containsExactly("B");
  }

  @Test
  void search_availability_excludesBlocked() {
    Actor owner = actor(Role.EMPLOYEE);
    Instant t = Instant.parse("2031-03-10T10:00:00Z");
    booking(room("A", 6, ""), owner, t, t.plusSeconds(3600), BookingStatus.CONFIRMED);
    booking(room("B", 6, ""), owner, t, t.plusSeconds(3600), BookingStatus.CANCELLED);
    room("C", 6, "");

    Resp overlapping = get(employee, ROOMS + "?availableFrom=2031-03-10T10:30:00Z&availableTo=2031-03-10T11:30:00Z");
    Resp adjacent = get(employee, ROOMS + "?availableFrom=2031-03-10T11:00:00Z&availableTo=2031-03-10T12:00:00Z");

    assertThat(mine(overlapping)).containsExactly("B", "C");
    assertThat(mine(adjacent)).containsExactly("A", "B", "C");
  }

  @ParameterizedTest(name = "GET /rooms?{0} -> 400")
  @ValueSource(strings = {
    "minCapacity=0", "minCapacity=1001", "minCapacity=-3", "minCapacity=abc",
    "equipments=TV", "equipments=a%20b", "equipments=tv,,whiteboard", "includeInactive=maybe",
    "availableFrom=not-a-date&availableTo=2031-03-10T10:00:00Z"
  })
  void search_badParams_400(String query) {
    assertProblem(get(employee, ROOMS + "?" + query), 400, "VALIDATION_FAILED");
  }

  @Test
  void search_tooManyEquipments_400() {
    String tokens = String.join(",", IntStream.range(0, 21).mapToObj(i -> "t" + i).toList());

    assertProblem(get(employee, ROOMS + "?equipments=" + tokens), 400, "VALIDATION_FAILED");
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"EMPLOYEE", "MANAGER"})
  void search_includeInactive_nonAdmin_403(Role role) {
    assertProblem(get(actor(role), ROOMS + "?includeInactive=true"), 403, "ACCESS_DENIED");
  }

  @Test
  void search_includeInactive_admin_listsInactive() {
    room("A", 6, "");
    Room inactive = room("B", 6, "");
    jdbc.update("UPDATE rooms SET is_active = FALSE WHERE id = ?", inactive.getId());

    assertThat(mine(get(admin, ROOMS + "?includeInactive=true"))).containsExactly("A", "B");
    assertThat(mine(get(admin, ROOMS))).containsExactly("A");
    assertThat(mine(get(admin, ROOMS + "?includeInactive=false"))).containsExactly("A");
  }

  // R38
  @Test
  void search_onlyAvailableFrom_400() {
    assertProblem(get(employee, ROOMS + "?availableFrom=2031-03-10T10:00:00Z"), 400, "VALIDATION_FAILED");
    assertProblem(get(employee, ROOMS + "?availableTo=2031-03-10T10:00:00Z"), 400, "VALIDATION_FAILED");
  }

  // R58
  @ParameterizedTest(name = "from={0} to={1}")
  @ValueSource(strings = {"2031-03-10T10:00:00Z,2031-03-10T10:00:00Z", "2031-03-10T10:00:00Z,2031-03-10T09:59:00Z", "2031-03-10T10:00:00Z,2031-03-09T10:00:00Z"})
  void search_toNotAfterFrom_400(String range) {
    String[] p = range.split(",");

    Resp r = get(employee, ROOMS + "?availableFrom=" + p[0] + "&availableTo=" + p[1]);

    assertProblem(r, 400, "VALIDATION_FAILED");
    assertThat(r.body().path("errors").isArray()).isTrue();
    assertThat(r.body().path("errors").size()).isPositive();
  }

  // ---------------- A05 GET /rooms/{id} ----------------

  @Test
  void get_200() {
    Room room = room("A", 6, "tv");

    Resp r = get(employee, ROOMS + "/" + room.getId());

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body().path("id").asText()).isEqualTo(room.getId().toString());
    assertThat(r.body().path("name").asText()).isEqualTo(name("A"));
    assertThat(strings(r.body().path("equipments"))).containsExactly("tv");
    assertThat(r.body().path("active").asBoolean()).isTrue();
  }

  @Test
  void get_unknown_404() {
    assertProblem(get(employee, ROOMS + "/" + UUID.randomUUID()), 404, "ROOM_NOT_FOUND");
  }

  // R39
  @Test
  void getInactive_employee_404() {
    Room inactive = inactiveRoom();

    assertProblem(get(employee, ROOMS + "/" + inactive.getId()), 404, "ROOM_NOT_FOUND");
    assertProblem(get(actor(Role.MANAGER), ROOMS + "/" + inactive.getId()), 404, "ROOM_NOT_FOUND");
  }

  @Test
  void getInactive_admin_200() {
    Room inactive = inactiveRoom();

    Resp r = get(admin, ROOMS + "/" + inactive.getId());

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body().path("active").asBoolean()).isFalse();
  }

  // ---------------- A06 POST /rooms ----------------

  @Test
  void create_201() {
    Map<String, Object> req = body("A");
    req.put("active", false); // ignored on create: rooms start active

    Resp r = postJson(admin, ROOMS, req);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    assertThat(r.body().path("name").asText()).isEqualTo(name("A"));
    assertThat(r.body().path("location").asText()).isEqualTo("Tầng 2");
    assertThat(r.body().path("capacity").asInt()).isEqualTo(8);
    assertThat(strings(r.body().path("equipments"))).containsExactly("tv", "whiteboard");
    assertThat(r.body().path("requiresApproval").asBoolean()).isTrue();
    assertThat(r.body().path("active").asBoolean()).isTrue();
    UUID id = UUID.fromString(r.body().path("id").asText());
    assertThat(jdbc.queryForObject("SELECT equipments FROM rooms WHERE id = ?", String.class, id)).isEqualTo("tv,whiteboard");
    assertThat(get(employee, ROOMS + "/" + id).status()).isEqualTo(200);
  }

  @Test
  void create_noEquipments_storedEmpty() {
    Resp r = postJson(admin, ROOMS, roomBody(name("A"), 4, List.of(), false));

    assertThat(r.status()).isEqualTo(201);
    assertThat(r.body().path("equipments").isArray()).isTrue();
    assertThat(r.body().path("equipments").size()).isZero();
    assertThat(jdbc.queryForObject("SELECT equipments FROM rooms WHERE id = ?", String.class, UUID.fromString(r.body().path("id").asText()))).isEmpty();
  }

  static Stream<Arguments> invalidRoomBodies() {
    return Stream.of(
        Arguments.of("blank name", (Consumer<Map<String, Object>>) m -> m.put("name", "")),
        Arguments.of("missing name", (Consumer<Map<String, Object>>) m -> m.remove("name")),
        Arguments.of("name 101 chars", (Consumer<Map<String, Object>>) m -> m.put("name", "n".repeat(101))),
        Arguments.of("blank location", (Consumer<Map<String, Object>>) m -> m.put("location", "")),
        Arguments.of("location 256 chars", (Consumer<Map<String, Object>>) m -> m.put("location", "l".repeat(256))),
        Arguments.of("capacity 0", (Consumer<Map<String, Object>>) m -> m.put("capacity", 0)),
        Arguments.of("capacity 1001", (Consumer<Map<String, Object>>) m -> m.put("capacity", 1001)),
        Arguments.of("capacity missing", (Consumer<Map<String, Object>>) m -> m.remove("capacity")),
        Arguments.of("capacity not a number", (Consumer<Map<String, Object>>) m -> m.put("capacity", "many")),
        Arguments.of("equipments missing", (Consumer<Map<String, Object>>) m -> m.remove("equipments")),
        Arguments.of("equipment upper case", (Consumer<Map<String, Object>>) m -> m.put("equipments", List.of("TV"))),
        Arguments.of("equipment with space", (Consumer<Map<String, Object>>) m -> m.put("equipments", List.of("big tv"))),
        Arguments.of("equipment 25 chars", (Consumer<Map<String, Object>>) m -> m.put("equipments", List.of("a".repeat(25)))),
        Arguments.of("equipment empty", (Consumer<Map<String, Object>>) m -> m.put("equipments", List.of(""))),
        Arguments.of("duplicate equipments", (Consumer<Map<String, Object>>) m -> m.put("equipments", List.of("tv", "tv"))),
        Arguments.of("21 equipments", (Consumer<Map<String, Object>>) m -> m.put("equipments", IntStream.range(0, 21).mapToObj(i -> "t" + i).toList())),
        Arguments.of("requiresApproval missing", (Consumer<Map<String, Object>>) m -> m.remove("requiresApproval")),
        Arguments.of("unknown property", (Consumer<Map<String, Object>>) m -> m.put("floor", 3)));
  }

  @ParameterizedTest(name = "create_invalid_400: {0}")
  @MethodSource("invalidRoomBodies")
  void create_invalid_400(String name, Consumer<Map<String, Object>> mutate) {
    Map<String, Object> req = body("A");
    mutate.accept(req);

    Resp r = postJson(admin, ROOMS, req);

    assertProblem(r, 400, "VALIDATION_FAILED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms WHERE name = ?", Integer.class, name("A"))).isZero();
  }

  @Test
  void create_duplicateName_409() {
    assertThat(postJson(admin, ROOMS, body("A")).status()).isEqualTo(201);

    assertProblem(postJson(admin, ROOMS, body("A")), 409, "ROOM_NAME_ALREADY_EXISTS");
  }

  @Test
  void create_nameIsCaseSensitive() {
    assertThat(postJson(admin, ROOMS, roomBody(name("abc"), 4, List.of(), false)).status()).isEqualTo(201);
    assertThat(postJson(admin, ROOMS, roomBody(name("ABC"), 4, List.of(), false)).status()).isEqualTo(201);
  }

  // ---------------- A07 PUT /rooms/{id} ----------------

  @Test
  void update_200() {
    Room room = room("A", 6, "tv");
    Map<String, Object> req = roomBody(name("A2"), 20, List.of("microphone", "projector"), true);

    Resp r = as(admin, "PUT", ROOMS + "/" + room.getId(), req);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("id").asText()).isEqualTo(room.getId().toString());
    assertThat(r.body().path("name").asText()).isEqualTo(name("A2"));
    assertThat(r.body().path("capacity").asInt()).isEqualTo(20);
    assertThat(strings(r.body().path("equipments"))).containsExactly("microphone", "projector");
    assertThat(r.body().path("requiresApproval").asBoolean()).isTrue();
    assertThat(jdbc.queryForObject("SELECT name FROM rooms WHERE id = ?", String.class, room.getId())).isEqualTo(name("A2"));
  }

  @Test
  void update_sameNameOwnRoom_200() {
    Room room = room("A", 6, "tv");

    Resp r = as(admin, "PUT", ROOMS + "/" + room.getId(), roomBody(name("A"), 7, List.of("tv"), false));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("capacity").asInt()).isEqualTo(7);
  }

  @Test
  void update_invalid_400() {
    Room room = room("A", 6, "tv");

    assertProblem(as(admin, "PUT", ROOMS + "/" + room.getId(), roomBody("", 0, List.of("TV"), false)), 400, "VALIDATION_FAILED");
    assertThat(jdbc.queryForObject("SELECT capacity FROM rooms WHERE id = ?", Integer.class, room.getId())).isEqualTo(6);
  }

  @Test
  void update_unknown_404() {
    assertProblem(as(admin, "PUT", ROOMS + "/" + UUID.randomUUID(), body("A")), 404, "ROOM_NOT_FOUND");
  }

  @Test
  void update_duplicateName_409() {
    room("A", 6, "");
    Room other = room("B", 6, "");

    assertProblem(as(admin, "PUT", ROOMS + "/" + other.getId(), roomBody(name("A"), 6, List.of(), false)), 409, "ROOM_NAME_ALREADY_EXISTS");
  }

  // R41
  @Test
  void put_activeTrue_reactivates() {
    Room inactive = inactiveRoom();
    Map<String, Object> req = roomBody(inactive.getName(), 10, List.of(), false);
    req.put("active", true);

    Resp r = as(admin, "PUT", ROOMS + "/" + inactive.getId(), req);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("active").asBoolean()).isTrue();
    assertThat(get(employee, ROOMS + "/" + inactive.getId()).status()).isEqualTo(200);
    Instant start = inMinutes(120);
    assertThat(postJson(employee, BOOKINGS, bookingBody(inactive.getId(), start, start.plusSeconds(3600))).status()).isEqualTo(201);
  }

  // R60
  @Test
  void put_activeOmitted_inactiveStaysInactive() {
    Room inactive = inactiveRoom();

    Resp r = as(admin, "PUT", ROOMS + "/" + inactive.getId(), roomBody(inactive.getName(), 11, List.of(), false));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("active").asBoolean()).isFalse();
    assertThat(r.body().path("capacity").asInt()).isEqualTo(11);
    assertProblem(get(employee, ROOMS + "/" + inactive.getId()), 404, "ROOM_NOT_FOUND");
  }

  @Test
  void put_activeOmitted_activeStaysActive() {
    Room room = room("A", 6, "");

    Resp r = as(admin, "PUT", ROOMS + "/" + room.getId(), roomBody(name("A"), 6, List.of(), false));

    assertThat(r.body().path("active").asBoolean()).isTrue();
  }

  @Test
  void put_activeFalse_deactivates() {
    Room room = room("A", 6, "");
    Map<String, Object> req = roomBody(name("A"), 6, List.of(), false);
    req.put("active", false);

    Resp r = as(admin, "PUT", ROOMS + "/" + room.getId(), req);

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("active").asBoolean()).isFalse();
  }

  // R40
  @Test
  void put_activeFalse_withFutureBookings_409() {
    Room room = room("A", 6, "");
    booking(room, actor(Role.EMPLOYEE), inMinutes(60), inMinutes(120), BookingStatus.CONFIRMED);
    Map<String, Object> req = roomBody(name("A"), 6, List.of(), false);
    req.put("active", false);

    assertProblem(as(admin, "PUT", ROOMS + "/" + room.getId(), req), 409, "ROOM_HAS_FUTURE_BOOKINGS");
    assertThat(jdbc.queryForObject("SELECT is_active FROM rooms WHERE id = ?", Boolean.class, room.getId())).isTrue();
  }

  // R28: a concurrent committed change of the room between load and flush -> CONCURRENT_UPDATE
  @Test
  void update_concurrent_409ConcurrentUpdate() throws Exception {
    Room room = room("A", 6, "");

    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try (Statement s = c.createStatement()) {
        s.executeUpdate("UPDATE rooms SET version = version + 1 WHERE id = '" + room.getId() + "'");
      }
      CompletableFuture<Resp> request =
          CompletableFuture.supplyAsync(() -> as(admin, "PUT", ROOMS + "/" + room.getId(), roomBody(name("A-new"), 9, List.of(), false)));
      awaitBlockedOnLock(request);
      c.commit();

      assertProblem(request.get(), 409, "CONCURRENT_UPDATE");
    }

    assertThat(jdbc.queryForObject("SELECT name FROM rooms WHERE id = ?", String.class, room.getId())).isEqualTo(name("A"));
  }

  // ---------------- A08 DELETE /rooms/{id} ----------------

  @Test
  void delete_204() {
    Room room = room("A", 6, "");
    Actor owner = actor(Role.EMPLOYEE);
    Instant now = Instant.now();
    var past = booking(room, owner, now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS), BookingStatus.CONFIRMED);

    Resp r = as(admin, "DELETE", ROOMS + "/" + room.getId(), null);

    assertThat(r.status()).isEqualTo(204);
    assertThat(r.body().isMissingNode() || r.body().isEmpty()).isTrue();
    assertThat(jdbc.queryForObject("SELECT is_active FROM rooms WHERE id = ?", Boolean.class, room.getId())).isFalse();
    assertThat(statusOf(past.getId())).as("existing bookings keep their status").isEqualTo("CONFIRMED");
    assertProblem(get(employee, ROOMS + "/" + room.getId()), 404, "ROOM_NOT_FOUND");
  }

  @Test
  void delete_again_204() {
    Room room = room("A", 6, "");

    assertThat(as(admin, "DELETE", ROOMS + "/" + room.getId(), null).status()).isEqualTo(204);
    assertThat(as(admin, "DELETE", ROOMS + "/" + room.getId(), null).status()).isEqualTo(204);
  }

  @Test
  void delete_unknown_404() {
    assertProblem(as(admin, "DELETE", ROOMS + "/" + UUID.randomUUID(), null), 404, "ROOM_NOT_FOUND");
  }

  // R40
  @ParameterizedTest
  @EnumSource(value = BookingStatus.class, names = {"PENDING", "CONFIRMED", "CHECKED_IN"})
  void delete_withFutureBookings_409(BookingStatus status) {
    Room room = room("A", 6, "");
    booking(room, actor(Role.EMPLOYEE), inMinutes(60), inMinutes(120), status);

    assertProblem(as(admin, "DELETE", ROOMS + "/" + room.getId(), null), 409, "ROOM_HAS_FUTURE_BOOKINGS");
    assertThat(jdbc.queryForObject("SELECT is_active FROM rooms WHERE id = ?", Boolean.class, room.getId())).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "NO_SHOW", "COMPLETED"})
  void delete_withNonBlockingFutureBookings_204(BookingStatus status) {
    Room room = room("A", 6, "");
    booking(room, actor(Role.EMPLOYEE), inMinutes(60), inMinutes(120), status);

    assertThat(as(admin, "DELETE", ROOMS + "/" + room.getId(), null).status()).isEqualTo(204);
  }

  // R40: "blocking booking with end > now" - strictly greater
  @Test
  void delete_guardBoundary_endEqualsNow() {
    Actor owner = actor(Role.EMPLOYEE);
    Instant now = inMinutes(10);
    Room endsNow = room("A", 6, "");
    Room endsAfter = room("B", 6, "");
    booking(endsNow, owner, now.minus(60, ChronoUnit.MINUTES), now, BookingStatus.CONFIRMED);
    booking(endsAfter, owner, now.minus(60, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.MINUTES), BookingStatus.CONFIRMED);
    clock.set(now);

    assertThat(as(admin, "DELETE", ROOMS + "/" + endsNow.getId(), null).status()).isEqualTo(204);
    assertProblem(as(admin, "DELETE", ROOMS + "/" + endsAfter.getId(), null), 409, "ROOM_HAS_FUTURE_BOOKINGS");
  }
}
