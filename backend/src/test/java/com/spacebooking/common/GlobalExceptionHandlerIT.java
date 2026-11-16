package com.spacebooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GlobalExceptionHandlerIT extends AbstractDomainIT {

  static Stream<Arguments> malformedUuidRequests() {
    String b = BOOKINGS + "/not-a-uuid";
    String r = ROOMS + "/not-a-uuid";
    Map<String, Object> reject = Map.of("reason", "x");
    Map<String, Object> room = roomBody("Phòng", 4, List.of(), false);
    return Stream.of(
        Arguments.of(Role.MANAGER, "PATCH", b + "/approve", null),
        Arguments.of(Role.MANAGER, "PATCH", b + "/reject", reject),
        Arguments.of(Role.EMPLOYEE, "PATCH", b + "/check-in", null),
        Arguments.of(Role.EMPLOYEE, "PATCH", b + "/checkout", null),
        Arguments.of(Role.EMPLOYEE, "PATCH", b + "/cancel", null),
        Arguments.of(Role.EMPLOYEE, "GET", b + "/logs", null),
        Arguments.of(Role.ADMIN, "GET", b + "/logs", null),
        Arguments.of(Role.EMPLOYEE, "GET", r, null),
        Arguments.of(Role.ADMIN, "PUT", r, room),
        Arguments.of(Role.ADMIN, "DELETE", r, null));
  }

  // R59
  @ParameterizedTest(name = "{0} {1} {2} -> 400")
  @MethodSource("malformedUuidRequests")
  void malformedUuid_400(Role role, String method, String path, Object body) {
    Resp resp = as(actor(role), method, path, body);

    assertProblem(resp, 400, "VALIDATION_FAILED");
    assertThat(resp.body().toString()).as("no stack trace or exception class").doesNotContain("Exception").doesNotContain("\tat ");
  }

  // R26: the exclusion constraint is the last line of defence; here the race happens below the optimistic room lock
  @Test
  void exclusionViolation_409() throws Exception {
    Room room = room(false);
    Actor employee = actor(Role.EMPLOYEE);
    Actor other = actor(Role.EMPLOYEE);
    Instant start = inMinutes(300);
    Instant end = start.plus(60, ChronoUnit.MINUTES);

    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement ps =
          c.prepareStatement(
              "INSERT INTO bookings (id, room_id, user_id, title, start_time, end_time, status, created_at)"
                  + " VALUES (?, ?, ?, 'raw', ?, ?, 'CONFIRMED', ?)")) {
        ps.setObject(1, UUID.randomUUID());
        ps.setObject(2, room.getId());
        ps.setObject(3, other.id());
        ps.setObject(4, utc(start));
        ps.setObject(5, utc(end));
        ps.setObject(6, LocalDateTime.now());
        ps.executeUpdate(); // uncommitted: invisible to the service's overlap query, but still conflicts on insert
      }
      CompletableFuture<Resp> request =
          CompletableFuture.supplyAsync(() -> postJson(employee, BOOKINGS, bookingBody(room.getId(), start.plusSeconds(1800), end.plusSeconds(1800))));
      awaitBlockedOnLock(request);
      c.commit();

      assertProblem(request.get(), 409, "BOOKING_CONFLICT");
    }

    assertThat(blockingCount(room.getId())).isEqualTo(1);
  }

  // A real unexpected failure: the table the room search reads is briefly missing.
  @Test
  void unexpected_500_noStackTrace() {
    Actor employee = actor(Role.EMPLOYEE);
    jdbc.execute("ALTER TABLE rooms RENAME TO rooms_gone");
    Resp resp;
    try {
      resp = get(employee, ROOMS);
    } finally {
      jdbc.execute("ALTER TABLE rooms_gone RENAME TO rooms");
    }

    assertProblem(resp, 500, "INTERNAL_ERROR");
    assertThat(resp.body().toString())
        .as("no stack trace, exception class or SQL")
        .doesNotContain("Exception")
        .doesNotContain("\tat ")
        .doesNotContain("rooms_gone")
        .doesNotContainIgnoringCase("select");
  }

  @Test
  void unknownRoute_404() {
    assertProblem(get(actor(Role.EMPLOYEE), API + "/no-such-route"), 404, "REQUEST_NOT_SUPPORTED");
  }

  @Test
  void unsupportedMediaType_415() throws Exception {
    Actor employee = actor(Role.EMPLOYEE);
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + BOOKINGS))
            .header("Authorization", "Bearer " + employee.token())
            .header("Content-Type", "text/plain")
            .POST(HttpRequest.BodyPublishers.ofString("not json"))
            .build();
    HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

    assertProblem(
        new Resp(res.statusCode(), res.headers().firstValue("content-type").orElse(""), mapper.readTree(res.body())),
        415,
        "REQUEST_NOT_SUPPORTED");
  }
}
