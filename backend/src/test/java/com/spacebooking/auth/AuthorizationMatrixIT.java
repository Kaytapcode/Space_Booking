package com.spacebooking.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Permission matrix (domain.md section 9): Phase 2 rows first, Phase 3 endpoints and ownership below. */
class AuthorizationMatrixIT extends AbstractDomainIT {

  private static final String ME = "/api/v1/auth/me";
  private static final String AUDIT = "/api/v1/audit-logs";

  /** role: NONE or a Role name; code: expected ProblemDetail code, null for 2xx. */
  static Stream<Arguments> matrix() {
    return Stream.of(
        Arguments.of(ME, "NONE", 401, "UNAUTHENTICATED"),
        Arguments.of(ME, "EMPLOYEE", 200, null),
        Arguments.of(ME, "MANAGER", 200, null),
        Arguments.of(ME, "ADMIN", 200, null),
        Arguments.of(AUDIT, "NONE", 401, "UNAUTHENTICATED"),
        Arguments.of(AUDIT, "EMPLOYEE", 403, "ACCESS_DENIED"),
        Arguments.of(AUDIT, "MANAGER", 403, "ACCESS_DENIED"),
        Arguments.of(AUDIT, "ADMIN", 200, null));
  }

  @ParameterizedTest(name = "GET {0} as {1} -> {2}")
  @MethodSource("matrix")
  void matrix(String path, String role, int status, String code) {
    String token = "NONE".equals(role) ? null : tokenFor(Role.valueOf(role));

    Resp r = get(path, token);

    if (code != null) {
      assertProblem(r, status, code);
    } else {
      assertThat(r.status()).as("body: %s", r.body()).isEqualTo(status);
    }
  }

  @Test
  void auditLogs_admin_returnsPage() {
    Resp r = get(AUDIT, tokenFor(Role.ADMIN));

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body().path("content").isArray()).isTrue();
    assertThat(r.body().path("page").asInt()).isZero();
    assertThat(r.body().has("size")).isTrue();
    assertThat(r.body().has("totalElements")).isTrue();
    assertThat(r.body().has("totalPages")).isTrue();
  }

  // R46
  @Test
  void expiredToken_401() {
    TestUser u = createUser(Role.ADMIN);
    String id = userId(u.email());
    // control: same signing scheme, still valid -> accepted (so the 401 below is due to expiry only)
    String valid = signedToken(id, u.email(), "ADMIN", Instant.now().plus(1, ChronoUnit.HOURS));
    assertThat(get(ME, valid).status()).isEqualTo(200);

    String expired = signedToken(id, u.email(), "ADMIN", Instant.now().minus(1, ChronoUnit.HOURS));

    assertProblem(get(ME, expired), 401, "UNAUTHENTICATED");
    assertProblem(get(AUDIT, expired), 401, "UNAUTHENTICATED");
  }

  // Z05
  @Test
  void forgedRoleToken_401() {
    String employeeToken = tokenFor(Role.EMPLOYEE);
    assertThat(get(AUDIT, employeeToken).status()).isEqualTo(403); // control: genuinely not an admin

    String forged = withForgedRole(employeeToken, "ADMIN");

    assertThat(forged).isNotEqualTo(employeeToken);
    assertProblem(get(AUDIT, forged), 401, "UNAUTHENTICATED");
    assertProblem(get(ME, forged), 401, "UNAUTHENTICATED");
  }

  @Test
  void unsignedAlgNoneToken_401() {
    TestUser u = createUser(Role.EMPLOYEE);
    String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
    String payload =
        b64(
            "{\"sub\":\"%s\",\"email\":\"%s\",\"role\":\"ADMIN\",\"exp\":%d}"
                .formatted(userId(u.email()), u.email(), Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond()));

    assertProblem(get(AUDIT, header + "." + payload + "."), 401, "UNAUTHENTICATED");
  }

  @Test
  void wrongSecretToken_401() {
    TestUser u = createUser(Role.ADMIN);
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload =
        b64(
            "{\"sub\":\"%s\",\"email\":\"%s\",\"role\":\"ADMIN\",\"exp\":%d}"
                .formatted(userId(u.email()), u.email(), Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond()));
    String token = header + "." + payload + "." + hs256(header + "." + payload, "some-other-secret-some-other-secret-0123");

    assertProblem(get(ME, token), 401, "UNAUTHENTICATED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Bearer ", "Bearer garbage", "Bearer a.b.c", "Basic Zm9vOmJhcg==", "Token abc", "garbage"})
  void malformedAuthorization_401(String authorization) {
    assertProblem(call("GET", ME, authorization, null), 401, "UNAUTHENTICATED");
    assertProblem(call("GET", AUDIT, authorization, null), 401, "UNAUTHENTICATED");
  }

  // ---------------- Z01: every other endpoint x {none, EMPLOYEE, MANAGER, ADMIN} ----------------

  private static void endpoint(
      List<Arguments> out,
      String method,
      String path,
      String idKind,
      String bodyKind,
      String notFoundCode,
      int none,
      int employee,
      int manager,
      int admin) {
    String[] roles = {"NONE", "EMPLOYEE", "MANAGER", "ADMIN"};
    int[] statuses = {none, employee, manager, admin};
    for (int i = 0; i < 4; i++) {
      String code =
          switch (statuses[i]) {
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> notFoundCode;
            default -> null;
          };
      out.add(Arguments.of(method, path, idKind, bodyKind, roles[i], statuses[i], code));
    }
  }

  /** idKind: ROOM_ACTIVE, ROOM_INACTIVE, ROOM_FRESH, UNKNOWN or NONE; the id replaces {id} in the path. */
  static Stream<Arguments> domainMatrix() {
    String rooms = "/api/v1/rooms";
    String bookings = "/api/v1/bookings";
    List<Arguments> m = new ArrayList<>();
    // public (any Authorization is ignored)
    endpoint(m, "POST", "/api/v1/auth/register", "NONE", "REGISTER", null, 201, 201, 201, 201);
    endpoint(m, "POST", "/api/v1/auth/login", "NONE", "LOGIN", null, 200, 200, 200, 200);
    // rooms
    endpoint(m, "GET", rooms, "NONE", "NONE", null, 401, 200, 200, 200);
    endpoint(m, "GET", rooms + "?includeInactive=true", "NONE", "NONE", null, 401, 403, 403, 200);
    // Percent-encoded path must not bypass the admin-only rule (review phase3 F1).
    endpoint(m, "GET", "/api/v1/%72ooms?includeInactive=true", "NONE", "NONE", null, 401, 403, 403, 200);
    endpoint(m, "GET", rooms + "/{id}", "ROOM_ACTIVE", "NONE", "ROOM_NOT_FOUND", 401, 200, 200, 200);
    endpoint(m, "GET", rooms + "/{id}", "ROOM_INACTIVE", "NONE", "ROOM_NOT_FOUND", 401, 404, 404, 200);
    endpoint(m, "POST", rooms, "NONE", "ROOM_NEW", null, 401, 403, 403, 201);
    endpoint(m, "PUT", rooms + "/{id}", "ROOM_FRESH", "ROOM_NEW", "ROOM_NOT_FOUND", 401, 403, 403, 200);
    endpoint(m, "DELETE", rooms + "/{id}", "ROOM_FRESH", "NONE", "ROOM_NOT_FOUND", 401, 403, 403, 204);
    // bookings
    endpoint(m, "GET", bookings + "/daily-schedule?date=2031-03-10", "NONE", "NONE", null, 401, 200, 200, 200);
    endpoint(m, "GET", bookings + "/my-bookings", "NONE", "NONE", null, 401, 200, 200, 403);
    endpoint(m, "GET", bookings + "/pending", "NONE", "NONE", null, 401, 403, 200, 403);
    endpoint(m, "POST", bookings, "NONE", "BOOKING_UNKNOWN_ROOM", "ROOM_NOT_FOUND", 401, 404, 404, 403);
    endpoint(m, "PATCH", bookings + "/{id}/approve", "UNKNOWN", "NONE", "BOOKING_NOT_FOUND", 401, 403, 404, 403);
    endpoint(m, "PATCH", bookings + "/{id}/reject", "UNKNOWN", "REJECT", "BOOKING_NOT_FOUND", 401, 403, 404, 403);
    endpoint(m, "PATCH", bookings + "/{id}/check-in", "UNKNOWN", "NONE", "BOOKING_NOT_FOUND", 401, 404, 404, 403);
    endpoint(m, "PATCH", bookings + "/{id}/checkout", "UNKNOWN", "NONE", "BOOKING_NOT_FOUND", 401, 404, 404, 403);
    endpoint(m, "PATCH", bookings + "/{id}/cancel", "UNKNOWN", "NONE", "BOOKING_NOT_FOUND", 401, 404, 404, 403);
    endpoint(m, "GET", bookings + "/{id}/logs", "UNKNOWN", "NONE", "BOOKING_NOT_FOUND", 401, 404, 404, 404);
    return m.stream();
  }

  @ParameterizedTest(name = "{0} {1} [{2}] as {4} -> {5}")
  @MethodSource("domainMatrix")
  void matrixAllEndpoints(String method, String path, String idKind, String bodyKind, String role, int status, String code) {
    Actor actor = "NONE".equals(role) ? null : actor(Role.valueOf(role));
    String id =
        switch (idKind) {
          case "ROOM_ACTIVE", "ROOM_FRESH" -> room(false).getId().toString();
          case "ROOM_INACTIVE" -> inactiveRoom().getId().toString();
          case "UNKNOWN" -> UUID.randomUUID().toString();
          default -> "";
        };
    Object body =
        switch (bodyKind) {
          case "REGISTER" ->
              Map.of("email", "reg." + UUID.randomUUID() + "@test.local", "password", "Matkhau@2026", "fullName", "Nguyễn Văn An");
          case "LOGIN" -> {
            TestUser u = createUser(Role.EMPLOYEE);
            yield Map.of("email", u.email(), "password", u.password());
          }
          case "ROOM_NEW" -> roomBody(unique("MX"), 6, List.of("tv"), false);
          case "BOOKING_UNKNOWN_ROOM" -> bookingBody(UUID.randomUUID(), inMinutes(120), inMinutes(180));
          case "REJECT" -> Map.of("reason", "Không phù hợp");
          default -> null;
        };

    Resp r = as(actor, method, path.replace("{id}", id), body);

    if (code != null) {
      assertProblem(r, status, code);
    } else {
      assertThat(r.status()).as("body: %s", r.body()).isEqualTo(status);
    }
  }

  // ---------------- Z02 / Z03 ownership (IDOR) ----------------

  private Booking confirmedBookingOf(Actor owner) {
    Instant start = inMinutes(120);
    return booking(room(false), owner, start, start.plus(1, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
  }

  // Z02
  @ParameterizedTest(name = "other employee {0} {1} -> 403")
  @CsvSource({"PATCH, check-in", "PATCH, checkout", "PATCH, cancel", "GET, logs"})
  void idor(String method, String action) {
    Actor owner = actor(Role.EMPLOYEE);
    Actor attacker = actor(Role.EMPLOYEE);
    Booking b = confirmedBookingOf(owner);
    String path = BOOKINGS + "/" + b.getId() + "/" + action;

    assertProblem(as(attacker, method, path, null), 403, "ACCESS_DENIED");
    assertThat(statusOf(b.getId())).as("the booking is untouched").isEqualTo("CONFIRMED");
    assertThat(as(owner, "GET", BOOKINGS + "/" + b.getId() + "/logs", null).status()).as("control: owner reads logs").isEqualTo(200);
  }

  // Z03
  @Test
  void managerNotOwner_checkIn_403() {
    Booking b = confirmedBookingOf(actor(Role.EMPLOYEE));
    Actor manager = actor(Role.MANAGER);

    assertProblem(patch(manager, BOOKINGS + "/" + b.getId() + "/check-in"), 403, "ACCESS_DENIED");
    assertProblem(patch(manager, BOOKINGS + "/" + b.getId() + "/checkout"), 403, "ACCESS_DENIED");
    assertProblem(patch(manager, BOOKINGS + "/" + b.getId() + "/cancel"), 403, "ACCESS_DENIED");
    assertThat(statusOf(b.getId())).isEqualTo("CONFIRMED");
    assertThat(get(manager, BOOKINGS + "/" + b.getId() + "/logs").status()).as("manager reads any log").isEqualTo(200);
  }

  // Z04
  @Test
  void myBookings_isolation() {
    Actor a = actor(Role.EMPLOYEE);
    Actor b = actor(Role.EMPLOYEE);
    Actor manager = actor(Role.MANAGER);
    String ofA = confirmedBookingOf(a).getId().toString();
    String ofB = confirmedBookingOf(b).getId().toString();
    String ofManager = confirmedBookingOf(manager).getId().toString();

    assertThat(ids(get(a, BOOKINGS + "/my-bookings?size=100").body())).containsExactly(ofA);
    assertThat(ids(get(b, BOOKINGS + "/my-bookings?size=100").body())).containsExactly(ofB);
    assertThat(ids(get(manager, BOOKINGS + "/my-bookings?size=100").body())).containsExactly(ofManager);
  }

  private static List<String> ids(JsonNode page) {
    List<String> ids = new ArrayList<>();
    page.path("content").forEach(n -> ids.add(n.path("id").asText()));
    return ids;
  }
}
