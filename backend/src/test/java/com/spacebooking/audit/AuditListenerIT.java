package com.spacebooking.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingAction;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.domain.BookingStatusChangedEvent;
import com.spacebooking.booking.service.BookingService;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/** R29, R57 (domain.md section 8). */
class AuditListenerIT extends AbstractDomainIT {

  private static final Instant START = inMinutes(120);
  private static final Instant END = START.plus(60, ChronoUnit.MINUTES);

  @Autowired BookingService bookingService;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired TransactionTemplate tx;

  private record Outcome(UUID bookingId, String performedBy) {}

  private Outcome trigger(AuditAction action) {
    Actor employee = actor(Role.EMPLOYEE);
    Actor manager = actor(Role.MANAGER);
    return switch (action) {
      case CREATED -> {
        Resp r = postJson(employee, BOOKINGS, bookingBody(room(false).getId(), START, END));
        assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
        yield new Outcome(UUID.fromString(r.body().path("id").asText()), employee.email());
      }
      case APPROVED -> {
        Booking b = booking(room(true), employee, START, END, BookingStatus.PENDING);
        assertThat(patch(manager, BOOKINGS + "/" + b.getId() + "/approve").status()).isEqualTo(200);
        yield new Outcome(b.getId(), manager.email());
      }
      case REJECTED -> {
        Booking b = booking(room(true), employee, START, END, BookingStatus.PENDING);
        assertThat(as(manager, "PATCH", BOOKINGS + "/" + b.getId() + "/reject", Map.of("reason", "Hết chỗ")).status())
            .isEqualTo(200);
        yield new Outcome(b.getId(), manager.email());
      }
      case CANCELLED -> {
        Booking b = booking(room(false), employee, START, END, BookingStatus.CONFIRMED);
        assertThat(patch(employee, BOOKINGS + "/" + b.getId() + "/cancel").status()).isEqualTo(200);
        yield new Outcome(b.getId(), employee.email());
      }
      case CHECKED_IN -> {
        Booking b = booking(room(false), employee, START, END, BookingStatus.CONFIRMED);
        clock.set(START);
        assertThat(patch(employee, BOOKINGS + "/" + b.getId() + "/check-in").status()).isEqualTo(200);
        yield new Outcome(b.getId(), employee.email());
      }
      case CHECKED_OUT -> {
        Booking b = booking(room(false), employee, START, END, BookingStatus.CHECKED_IN);
        clock.set(START.plus(10, ChronoUnit.MINUTES));
        assertThat(patch(employee, BOOKINGS + "/" + b.getId() + "/checkout").status()).isEqualTo(200);
        yield new Outcome(b.getId(), employee.email());
      }
      case EXPIRED -> {
        Booking b = booking(room(true), employee, START, END, BookingStatus.PENDING);
        clock.set(START);
        bookingService.systemTransition(b.getId(), BookingAction.EXPIRE);
        yield new Outcome(b.getId(), "SYSTEM_SCHEDULER");
      }
      case NO_SHOW -> {
        Booking b = booking(room(false), employee, START, END, BookingStatus.CONFIRMED);
        clock.set(START.plus(16, ChronoUnit.MINUTES));
        bookingService.systemTransition(b.getId(), BookingAction.MARK_NO_SHOW);
        yield new Outcome(b.getId(), "SYSTEM_SCHEDULER");
      }
      case COMPLETED -> {
        Booking b = booking(room(false), employee, START, END, BookingStatus.CHECKED_IN);
        clock.set(END);
        bookingService.systemTransition(b.getId(), BookingAction.COMPLETE);
        yield new Outcome(b.getId(), "SYSTEM_SCHEDULER");
      }
    };
  }

  // R29, R57
  @ParameterizedTest
  @EnumSource(AuditAction.class)
  void transition_writesOneRow(AuditAction action) throws Exception {
    Outcome o = trigger(action);

    List<Map<String, Object>> rows = auditRows(o.bookingId());

    assertThat(rows).as("exactly one audit row per transition").hasSize(1);
    assertThat(rows.get(0).get("action")).isEqualTo(action.name());
    assertThat(rows.get(0).get("performed_by")).isEqualTo(o.performedBy());
    JsonNode details = mapper.readTree((String) rows.get(0).get("details"));
    assertThat(details.has("from")).isTrue();
    assertThat(details.path("to").asText()).isEqualTo(statusOf(o.bookingId()));
  }

  // R57
  @Test
  void performedBy_isActorEmail() {
    Actor employee = actor(Role.EMPLOYEE);
    Actor manager = actor(Role.MANAGER);
    Resp created = postJson(employee, BOOKINGS, bookingBody(room(true).getId(), START, END));
    UUID id = UUID.fromString(created.body().path("id").asText());

    assertThat(patch(manager, BOOKINGS + "/" + id + "/approve").status()).isEqualTo(200);
    assertThat(patch(employee, BOOKINGS + "/" + id + "/cancel").status()).isEqualTo(200);

    List<Map<String, Object>> rows = auditRows(id);
    assertThat(rows).extracting(r -> r.get("action")).containsExactly("CREATED", "APPROVED", "CANCELLED");
    assertThat(rows).extracting(r -> r.get("performed_by")).containsExactly(employee.email(), manager.email(), employee.email());
  }

  // R29: same transaction as the state change
  @Test
  void transition_rollbackLeavesNoRow() {
    Booking b = booking(room(false), actor(Role.EMPLOYEE), START, END, BookingStatus.CONFIRMED);
    BookingStatusChangedEvent event =
        new BookingStatusChangedEvent(
            b.getId(), BookingStatus.CONFIRMED, BookingStatus.CANCELLED, AuditAction.CANCELLED, "x@test.local", null, Instant.now());

    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      publisher.publishEvent(event);
                      throw new IllegalStateException("force rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(auditRows(b.getId())).as("rolled back with the surrounding transaction").isEmpty();

    tx.executeWithoutResult(s -> publisher.publishEvent(event));
    assertThat(auditRows(b.getId())).as("control: committed event is stored").hasSize(1);
  }
}
