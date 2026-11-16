package com.spacebooking.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.domain.BookingStatusChangedEvent;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/** R30 (domain.md section 8): the listener stores {"from","to"} plus "reason" on REJECTED and "endTime" on CHECKED_OUT. */
class AuditLogListenerTest extends AbstractDomainIT {

  @Autowired ApplicationEventPublisher publisher;
  @Autowired TransactionTemplate tx;

  private Booking booking;

  @BeforeEach
  void setUp() {
    Room room = room(false);
    booking = booking(room, actor(Role.EMPLOYEE), inMinutes(60), inMinutes(120), BookingStatus.PENDING);
  }

  private JsonNode publish(BookingStatus from, BookingStatus to, AuditAction action, String extra) {
    tx.executeWithoutResult(
        s ->
            publisher.publishEvent(
                new BookingStatusChangedEvent(booking.getId(), from, to, action, "who@test.local", extra, Instant.now())));
    List<Map<String, Object>> rows = auditRows(booking.getId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("action")).isEqualTo(action.name());
    assertThat(rows.get(0).get("performed_by")).isEqualTo("who@test.local");
    try {
      return mapper.readTree((String) rows.get(0).get("details"));
    } catch (Exception e) {
      throw new AssertionError("details is not JSON text: " + rows.get(0).get("details"), e);
    }
  }

  @Test
  void details_includesReason_and_endTime() {
    // reject: from, to, reason
    JsonNode reject = publish(BookingStatus.PENDING, BookingStatus.CANCELLED, AuditAction.REJECTED, "Phòng đang bảo trì");
    assertThat(reject.path("from").asText()).isEqualTo("PENDING");
    assertThat(reject.path("to").asText()).isEqualTo("CANCELLED");
    assertThat(reject.path("reason").asText()).isEqualTo("Phòng đang bảo trì");
    assertThat(reject.has("endTime")).isFalse();
  }

  @Test
  void details_checkout_includesEndTime() {
    Instant end = Instant.parse("2026-11-17T09:31:00Z");

    JsonNode d = publish(BookingStatus.CHECKED_IN, BookingStatus.COMPLETED, AuditAction.CHECKED_OUT, end.toString());

    assertThat(d.path("from").asText()).isEqualTo("CHECKED_IN");
    assertThat(d.path("to").asText()).isEqualTo("COMPLETED");
    assertThat(Instant.parse(d.path("endTime").asText())).isEqualTo(end);
    assertThat(d.has("reason")).isFalse();
  }

  @Test
  void details_created_hasNullFrom() {
    JsonNode d = publish(null, BookingStatus.CONFIRMED, AuditAction.CREATED, null);

    assertThat(d.has("from")).isTrue();
    assertThat(d.get("from").isNull()).isTrue();
    assertThat(d.path("to").asText()).isEqualTo("CONFIRMED");
    assertThat(d.has("reason")).isFalse();
    assertThat(d.has("endTime")).isFalse();
  }

  @Test
  void details_approved_onlyFromAndTo() {
    JsonNode d = publish(BookingStatus.PENDING, BookingStatus.CONFIRMED, AuditAction.APPROVED, null);

    assertThat(d.path("from").asText()).isEqualTo("PENDING");
    assertThat(d.path("to").asText()).isEqualTo("CONFIRMED");
    assertThat(d.size()).isEqualTo(2);
  }
}
