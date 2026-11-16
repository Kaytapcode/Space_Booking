package com.spacebooking.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A15 GET /bookings/{id}/logs and A16 GET /audit-logs. */
class AuditControllerIT extends AbstractDomainIT {

  @Test
  void logs_200_oldestFirst() {
    Actor owner = actor(Role.EMPLOYEE);
    Actor manager = actor(Role.MANAGER);
    Instant start = inMinutes(120);
    Resp created = postJson(owner, BOOKINGS, bookingBody(room(true).getId(), start, start.plusSeconds(3600)));
    String id = created.body().path("id").asText();
    assertThat(patch(manager, BOOKINGS + "/" + id + "/approve").status()).isEqualTo(200);
    assertThat(patch(owner, BOOKINGS + "/" + id + "/cancel").status()).isEqualTo(200);

    Resp r = get(owner, BOOKINGS + "/" + id + "/logs");

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().isArray()).isTrue();
    List<String> actions = new ArrayList<>();
    List<Instant> times = new ArrayList<>();
    for (JsonNode n : r.body()) {
      actions.add(n.path("action").asText());
      times.add(Instant.parse(n.path("createdAt").asText()));
      assertThat(n.path("bookingId").asText()).isEqualTo(id);
      assertThat(UUID.fromString(n.path("id").asText())).isNotNull();
      assertThat(n.path("details").asText()).startsWith("{");
    }
    assertThat(actions).containsExactly("CREATED", "APPROVED", "CANCELLED");
    assertThat(times).isSorted();
    assertThat(r.body().get(0).path("performedBy").asText()).isEqualTo(owner.email());
    assertThat(r.body().get(1).path("performedBy").asText()).isEqualTo(manager.email());
  }

  @Test
  void logs_managerAndAdmin_anyBooking_200() {
    Actor owner = actor(Role.EMPLOYEE);
    Instant start = inMinutes(120);
    String id = postJson(owner, BOOKINGS, bookingBody(room(false).getId(), start, start.plusSeconds(3600))).body().path("id").asText();

    assertThat(get(actor(Role.MANAGER), BOOKINGS + "/" + id + "/logs").status()).isEqualTo(200);
    assertThat(get(actor(Role.ADMIN), BOOKINGS + "/" + id + "/logs").status()).isEqualTo(200);
  }

  @Test
  void logs_unknown_404() {
    assertProblem(get(actor(Role.EMPLOYEE), BOOKINGS + "/" + UUID.randomUUID() + "/logs"), 404, "BOOKING_NOT_FOUND");
    assertProblem(get(actor(Role.MANAGER), BOOKINGS + "/" + UUID.randomUUID() + "/logs"), 404, "BOOKING_NOT_FOUND");
    assertProblem(get(actor(Role.ADMIN), BOOKINGS + "/" + UUID.randomUUID() + "/logs"), 404, "BOOKING_NOT_FOUND");
  }

  @Test
  void search_200_filters_newestFirst() {
    Actor owner = actor(Role.EMPLOYEE);
    Actor admin = actor(Role.ADMIN);
    Instant start = inMinutes(120);
    String id = postJson(owner, BOOKINGS, bookingBody(room(true).getId(), start, start.plusSeconds(3600))).body().path("id").asText();
    assertThat(patch(actor(Role.MANAGER), BOOKINGS + "/" + id + "/approve").status()).isEqualTo(200);
    clock.advance(Duration.ofMinutes(1));
    assertThat(patch(owner, BOOKINGS + "/" + id + "/cancel").status()).isEqualTo(200);
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
    String q = API + "/audit-logs?bookingId=" + id;

    Resp all = get(admin, q + "&from=" + today + "&to=" + today);
    assertThat(all.status()).as("body: %s", all.body()).isEqualTo(200);
    assertThat(all.body().path("totalElements").asLong()).isEqualTo(3);
    List<Instant> times = new ArrayList<>();
    for (JsonNode n : all.body().path("content")) {
      assertThat(n.path("bookingId").asText()).isEqualTo(id);
      times.add(Instant.parse(n.path("createdAt").asText()));
    }
    assertThat(times).isSortedAccordingTo(Comparator.reverseOrder());
    assertThat(all.body().path("content").get(0).path("action").asText()).isEqualTo("CANCELLED");

    Resp approved = get(admin, q + "&action=APPROVED");
    assertThat(approved.body().path("totalElements").asLong()).isEqualTo(1);
    assertThat(approved.body().path("content").get(0).path("action").asText()).isEqualTo("APPROVED");

    assertThat(get(admin, q + "&from=" + today.plusDays(1)).body().path("totalElements").asLong()).isZero();
    assertThat(get(admin, q + "&to=" + today.minusDays(1)).body().path("totalElements").asLong()).isZero();
  }

  @Test
  void search_fromAfterTo_400() {
    LocalDate d = LocalDate.of(2026, 1, 2);
    assertProblem(get(actor(Role.ADMIN), API + "/audit-logs?from=" + d + "&to=" + d.minusDays(1)), 400, "VALIDATION_FAILED");
  }
}
