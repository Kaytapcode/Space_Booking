package com.spacebooking.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** R63 (D35): every error the app emits for an operation is declared as an example of that operation. */
class OpenApiConformanceIT extends AbstractDomainIT {

  private static final String PROBLEM = "application/problem+json";
  private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch");

  /** One request that must fail with the given status and code. */
  private record Probe(Actor actor, String method, String path, Object body, int status, String code) {}

  @Test
  void errorCodes_declaredPerOperation() throws IOException {
    JsonNode contract = OpenApiContract.load(mapper);

    assertEveryDeclaredExampleIsConsistent(contract);

    Set<String> exercised = new HashSet<>();
    List<String> undeclared = new ArrayList<>();
    for (Probe p : probes()) {
      Resp r = as(p.actor(), p.method(), p.path(), p.body());
      assertProblem(r, p.status(), p.code());
      String actualCode = r.body().path("code").asText();
      String operation = operationOf(contract, p.method(), p.path());
      JsonNode declared = contract.at("/paths").path(operation.split(" ", 2)[1]).path(p.method().toLowerCase()).path("responses").path(String.valueOf(r.status()));
      Set<String> declaredCodes = declaredCodes(declared);
      if (!declaredCodes.contains(actualCode)) {
        undeclared.add("%s -> %d %s (declared: %s)".formatted(operation, r.status(), actualCode, declaredCodes));
      }
      assertThat(withoutQuery(r.body().path("instance").asText()))
          .as("instance of %s", operation)
          .isEqualTo(withoutQuery(p.path()));
      exercised.add(operation + " " + r.status() + " " + actualCode);
    }

    assertThat(undeclared).as("errors emitted but not declared as openapi examples of that operation").isEmpty();
    assertThat(exercised).as("distinct (operation, status, code) combinations exercised").hasSizeGreaterThanOrEqualTo(75);
  }

  // ---------------------------------------------------------------- static: what the contract declares

  /** D35: examples exist for every error status, match ErrorCode/status, and carry the operation's own instance path. */
  private void assertEveryDeclaredExampleIsConsistent(JsonNode contract) {
    int operations = 0;
    for (Iterator<Map.Entry<String, JsonNode>> paths = contract.path("paths").fields(); paths.hasNext(); ) {
      Map.Entry<String, JsonNode> path = paths.next();
      Pattern own = templateToPattern(path.getKey());
      for (String method : METHODS) {
        JsonNode op = path.getValue().path(method);
        if (op.isMissingNode()) {
          continue;
        }
        operations++;
        String where = method.toUpperCase() + " " + path.getKey();
        assertThat(op.path("responses").has("500")).as("%s declares 500", where).isTrue();
        for (Iterator<Map.Entry<String, JsonNode>> rs = op.path("responses").fields(); rs.hasNext(); ) {
          Map.Entry<String, JsonNode> response = rs.next();
          int status = Integer.parseInt(response.getKey());
          if (status < 400) {
            continue;
          }
          JsonNode examples = response.getValue().path("content").path(PROBLEM).path("examples");
          assertThat(examples.isEmpty()).as("%s %d has examples", where, status).isFalse();
          for (Iterator<JsonNode> it = examples.elements(); it.hasNext(); ) {
            JsonNode value = it.next().path("value");
            String code = value.path("code").asText();
            assertThat(value.path("status").asInt()).as("%s %d example %s: status", where, status, code).isEqualTo(status);
            assertThat(ErrorCode.valueOf(code).status().value()).as("%s example %s: ErrorCode status", where, code).isEqualTo(status);
            assertThat(own.matcher(withoutQuery(value.path("instance").asText())).matches())
                .as("%s %d example %s: instance %s is the operation's own path", where, status, code, value.path("instance").asText())
                .isTrue();
          }
          if (status == 500) {
            assertThat(declaredCodes(response.getValue())).as("%s 500", where).containsExactly("INTERNAL_ERROR");
          }
        }
      }
    }
    assertThat(operations).as("operations in the contract").isEqualTo(19);
  }

  private static Set<String> declaredCodes(JsonNode response) {
    Set<String> codes = new HashSet<>();
    response.path("content").path(PROBLEM).path("examples").forEach(e -> codes.add(e.path("value").path("code").asText()));
    return codes;
  }

  private static Pattern templateToPattern(String template) {
    return Pattern.compile(template.replaceAll("\\{[^}]+}", "[^/]+"));
  }

  private static String withoutQuery(String path) {
    return path.replaceAll("\\?.*", "");
  }

  /** "METHOD /template" of the contract operation a concrete request belongs to (fewest placeholders wins). */
  private static String operationOf(JsonNode contract, String method, String requestPath) {
    String path = withoutQuery(requestPath);
    String best = null;
    for (Iterator<String> it = contract.path("paths").fieldNames(); it.hasNext(); ) {
      String template = it.next();
      if (contract.path("paths").path(template).has(method.toLowerCase())
          && templateToPattern(template).matcher(path).matches()
          && (best == null || template.split("\\{", -1).length < best.split("\\{", -1).length)) {
        best = template;
      }
    }
    assertThat(best).as("%s %s is an operation of the contract", method, requestPath).isNotNull();
    return method.toUpperCase() + " " + best;
  }

  // ---------------------------------------------------------------- dynamic: errors the app emits

  private List<Probe> probes() {
    Actor employee = actor(Role.EMPLOYEE);
    Actor other = actor(Role.EMPLOYEE);
    Actor manager = actor(Role.MANAGER);
    Actor admin = actor(Role.ADMIN);

    Room room = room(false);
    Room approvalRoom = room(true);
    Room startedRoom = room(false);
    Booking future = booking(room, employee, inMinutes(600), inMinutes(660), BookingStatus.CONFIRMED);
    Booking startedPending = booking(approvalRoom, employee, inMinutes(-30), inMinutes(30), BookingStatus.PENDING);
    Booking startedConfirmed = booking(startedRoom, employee, inMinutes(-30), inMinutes(30), BookingStatus.CONFIRMED);

    String unknown = UUID.randomUUID().toString();
    String badId = "not-a-uuid";
    Map<String, Object> reason = Map.of("reason", "no capacity");
    Map<String, Object> validRoom = roomBody(unique("Free"), 5, List.of("tv"), false);
    Map<String, Object> dupRoomPost = roomBody(room.getName(), 5, List.of(), false);
    Map<String, Object> deactivateRoom = new LinkedHashMap<>(roomBody(room.getName(), 10, List.of(), false));
    deactivateRoom.put("active", false);

    String registered = unique("reg") + "@test.local";
    Map<String, Object> register = Map.of("email", registered, "password", PASSWORD, "fullName", "Reg User");
    assertThat(post(API + "/auth/register", register).status()).isEqualTo(201);

    List<Probe> p = new ArrayList<>();
    // auth
    p.add(new Probe(null, "POST", API + "/auth/register", Map.of(), 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "POST", API + "/auth/register", register, 409, "EMAIL_ALREADY_EXISTS"));
    p.add(new Probe(null, "POST", API + "/auth/login", Map.of("email", "not-an-email"), 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "POST", API + "/auth/login", Map.of("email", employee.email(), "password", "wrong-password"), 401, "INVALID_CREDENTIALS"));
    p.add(new Probe(null, "GET", API + "/auth/me", null, 401, "UNAUTHENTICATED"));

    // rooms
    p.add(new Probe(employee, "GET", ROOMS + "?availableFrom=2030-01-01T10:00:00Z&availableTo=2030-01-01T09:00:00Z", null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", ROOMS, null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(employee, "GET", ROOMS + "?includeInactive=true", null, 403, "ACCESS_DENIED"));
    p.add(new Probe(admin, "POST", ROOMS, Map.of(), 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "POST", ROOMS, validRoom, 401, "UNAUTHENTICATED"));
    p.add(new Probe(employee, "POST", ROOMS, validRoom, 403, "ACCESS_DENIED"));
    p.add(new Probe(admin, "POST", ROOMS, dupRoomPost, 409, "ROOM_NAME_ALREADY_EXISTS"));
    p.add(new Probe(employee, "GET", ROOMS + "/" + badId, null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", ROOMS + "/" + unknown, null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(employee, "GET", ROOMS + "/" + unknown, null, 404, "ROOM_NOT_FOUND"));
    p.add(new Probe(admin, "PUT", ROOMS + "/" + badId, validRoom, 400, "VALIDATION_FAILED"));
    p.add(new Probe(admin, "PUT", ROOMS + "/" + room.getId(), Map.of(), 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "PUT", ROOMS + "/" + unknown, validRoom, 401, "UNAUTHENTICATED"));
    p.add(new Probe(manager, "PUT", ROOMS + "/" + unknown, validRoom, 403, "ACCESS_DENIED"));
    p.add(new Probe(admin, "PUT", ROOMS + "/" + unknown, validRoom, 404, "ROOM_NOT_FOUND"));
    p.add(new Probe(admin, "PUT", ROOMS + "/" + approvalRoom.getId(), dupRoomPost, 409, "ROOM_NAME_ALREADY_EXISTS"));
    p.add(new Probe(admin, "PUT", ROOMS + "/" + room.getId(), deactivateRoom, 409, "ROOM_HAS_FUTURE_BOOKINGS"));
    p.add(new Probe(admin, "DELETE", ROOMS + "/" + badId, null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "DELETE", ROOMS + "/" + unknown, null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(employee, "DELETE", ROOMS + "/" + unknown, null, 403, "ACCESS_DENIED"));
    p.add(new Probe(admin, "DELETE", ROOMS + "/" + unknown, null, 404, "ROOM_NOT_FOUND"));
    p.add(new Probe(admin, "DELETE", ROOMS + "/" + room.getId(), null, 409, "ROOM_HAS_FUTURE_BOOKINGS"));

    // bookings: create
    p.add(new Probe(employee, "POST", BOOKINGS, Map.of(), 400, "VALIDATION_FAILED"));
    p.add(new Probe(employee, "POST", BOOKINGS, bookingBody(room.getId(), inMinutes(-120), inMinutes(-60)), 400, "START_IN_PAST"));
    p.add(new Probe(employee, "POST", BOOKINGS, bookingBody(room.getId(), inMinutes(1000), inMinutes(1121)), 400, "DURATION_EXCEEDED"));
    p.add(new Probe(null, "POST", BOOKINGS, bookingBody(room.getId(), inMinutes(1000), inMinutes(1060)), 401, "UNAUTHENTICATED"));
    p.add(new Probe(admin, "POST", BOOKINGS, bookingBody(room.getId(), inMinutes(1000), inMinutes(1060)), 403, "ACCESS_DENIED"));
    p.add(new Probe(employee, "POST", BOOKINGS, bookingBody(UUID.fromString(unknown), inMinutes(1000), inMinutes(1060)), 404, "ROOM_NOT_FOUND"));
    p.add(new Probe(other, "POST", BOOKINGS, bookingBody(room.getId(), inMinutes(630), inMinutes(690)), 409, "BOOKING_CONFLICT"));

    // bookings: reads
    p.add(new Probe(employee, "GET", BOOKINGS + "/daily-schedule?date=17-11-2026", null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", BOOKINGS + "/daily-schedule", null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(employee, "GET", BOOKINGS + "/my-bookings?size=500", null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", BOOKINGS + "/my-bookings", null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(admin, "GET", BOOKINGS + "/my-bookings", null, 403, "ACCESS_DENIED"));
    p.add(new Probe(manager, "GET", BOOKINGS + "/pending?page=-1", null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", BOOKINGS + "/pending", null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(employee, "GET", BOOKINGS + "/pending", null, 403, "ACCESS_DENIED"));

    // bookings: transitions (approve/reject are manager-only, the rest owner-only)
    for (String action : List.of("approve", "reject")) {
      Object body = "reject".equals(action) ? reason : null;
      String on = BOOKINGS + "/%s/" + action;
      p.add(new Probe(manager, "PATCH", on.formatted(badId), body, 400, "VALIDATION_FAILED"));
      p.add(new Probe(null, "PATCH", on.formatted(unknown), body, 401, "UNAUTHENTICATED"));
      p.add(new Probe(employee, "PATCH", on.formatted(unknown), body, 403, "ACCESS_DENIED"));
      p.add(new Probe(admin, "PATCH", on.formatted(unknown), body, 403, "ACCESS_DENIED"));
      p.add(new Probe(manager, "PATCH", on.formatted(unknown), body, 404, "BOOKING_NOT_FOUND"));
      p.add(new Probe(manager, "PATCH", on.formatted(future.getId()), body, 409, "INVALID_STATE_TRANSITION"));
      p.add(new Probe(manager, "PATCH", on.formatted(startedPending.getId()), body, 409, "BOOKING_ALREADY_STARTED"));
    }
    p.add(new Probe(manager, "PATCH", BOOKINGS + "/" + future.getId() + "/reject", Map.of(), 400, "VALIDATION_FAILED"));

    for (String action : List.of("check-in", "checkout", "cancel")) {
      String on = BOOKINGS + "/%s/" + action;
      p.add(new Probe(employee, "PATCH", on.formatted(badId), null, 400, "VALIDATION_FAILED"));
      p.add(new Probe(null, "PATCH", on.formatted(unknown), null, 401, "UNAUTHENTICATED"));
      p.add(new Probe(admin, "PATCH", on.formatted(unknown), null, 403, "ACCESS_DENIED"));
      p.add(new Probe(employee, "PATCH", on.formatted(unknown), null, 404, "BOOKING_NOT_FOUND"));
      p.add(new Probe(other, "PATCH", on.formatted(future.getId()), null, 403, "ACCESS_DENIED"));
    }
    p.add(new Probe(employee, "PATCH", BOOKINGS + "/" + future.getId() + "/check-in", null, 409, "OUTSIDE_CHECKIN_WINDOW"));
    p.add(new Probe(employee, "PATCH", BOOKINGS + "/" + future.getId() + "/checkout", null, 409, "INVALID_STATE_TRANSITION"));
    p.add(new Probe(employee, "PATCH", BOOKINGS + "/" + startedConfirmed.getId() + "/cancel", null, 409, "BOOKING_ALREADY_STARTED"));

    // logs and audit
    p.add(new Probe(employee, "GET", BOOKINGS + "/" + badId + "/logs", null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", BOOKINGS + "/" + unknown + "/logs", null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(other, "GET", BOOKINGS + "/" + future.getId() + "/logs", null, 403, "ACCESS_DENIED"));
    p.add(new Probe(admin, "GET", BOOKINGS + "/" + unknown + "/logs", null, 404, "BOOKING_NOT_FOUND"));
    p.add(new Probe(admin, "GET", API + "/audit-logs?from=2030-01-02&to=2030-01-01", null, 400, "VALIDATION_FAILED"));
    p.add(new Probe(null, "GET", API + "/audit-logs", null, 401, "UNAUTHENTICATED"));
    p.add(new Probe(manager, "GET", API + "/audit-logs", null, 403, "ACCESS_DENIED"));
    return p;
  }
}
