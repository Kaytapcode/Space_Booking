package com.spacebooking.booking.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.domain.Room;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

/** R02, R03, R04, R26 and the R01 boundaries at the SQL level. Existing slot is always 10:00-11:00 UTC. */
class BookingRepositoryIT extends AbstractDomainIT {

  private static Instant at(String hhmm) {
    return Instant.parse("2030-03-10T" + hhmm + ":00Z");
  }

  private Room room;
  private Actor owner;

  @BeforeEach
  void setUp() {
    room = room(false);
    owner = actor(Role.EMPLOYEE);
  }

  private boolean overlaps(Room r, String start, String end) {
    return bookingRepository.existsOverlap(r.getId(), utc(at(start)), utc(at(end)));
  }

  // R01 at repository level
  @ParameterizedTest(name = "{0}: new {1}-{2} -> overlap={3}")
  @CsvSource({
    "identical,10:00,11:00,true",
    "contained,10:15,10:45,true",
    "containing,09:00,12:00,true",
    "overlap left,09:30,10:30,true",
    "overlap right,10:30,11:30,true",
    "adjacent before,09:00,10:00,false",
    "adjacent after,11:00,12:00,false",
    "1-minute overlap left,09:00,10:01,true",
    "1-minute overlap right,10:59,12:00,true"
  })
  void existsOverlap_boundaries(String name, String start, String end, boolean expected) {
    booking(room, owner, at("10:00"), at("11:00"), BookingStatus.CONFIRMED);

    assertThat(overlaps(room, start, end)).as(name).isEqualTo(expected);
  }

  // R02
  @Test
  void existsOverlap_differentRoom_false() {
    Room other = room(false);
    booking(room, owner, at("10:00"), at("11:00"), BookingStatus.CONFIRMED);

    assertThat(overlaps(room, "10:00", "11:00")).as("control: same room conflicts").isTrue();
    assertThat(overlaps(other, "10:00", "11:00")).isFalse();
  }

  // R03
  @ParameterizedTest
  @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "NO_SHOW", "COMPLETED"})
  void existsOverlap_ignoresStatus(BookingStatus status) {
    booking(room, owner, at("10:00"), at("11:00"), status);

    assertThat(overlaps(room, "10:00", "11:00")).isFalse();
    assertThat(overlaps(room, "10:30", "10:45")).isFalse();
  }

  // R04
  @ParameterizedTest
  @EnumSource(value = BookingStatus.class, names = {"PENDING", "CONFIRMED", "CHECKED_IN"})
  void existsOverlap_blocks(BookingStatus status) {
    booking(room, owner, at("10:00"), at("11:00"), status);

    assertThat(overlaps(room, "10:00", "11:00")).isTrue();
    assertThat(overlaps(room, "10:59", "12:00")).isTrue();
    assertThat(overlaps(room, "11:00", "12:00")).as("adjacent after").isFalse();
  }

  // R26
  @Test
  void exclusionConstraint_rejectsOverlap() {
    booking(room, owner, at("10:00"), at("11:00"), BookingStatus.CONFIRMED);

    assertThatThrownBy(() -> booking(room, owner, at("10:30"), at("11:30"), BookingStatus.PENDING))
        .isInstanceOf(DataIntegrityViolationException.class)
        .satisfies(
            e -> {
              Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
              assertThat(root).isInstanceOf(SQLException.class);
              assertThat(((SQLException) root).getSQLState()).isEqualTo("23P01");
            });
    assertThat(blockingCount(room.getId())).isEqualTo(1);
  }

  @Test
  void exclusionConstraint_allowsAdjacentAndNonBlocking() {
    booking(room, owner, at("10:00"), at("11:00"), BookingStatus.CONFIRMED);

    booking(room, owner, at("11:00"), at("12:00"), BookingStatus.CONFIRMED); // adjacent
    booking(room, owner, at("10:00"), at("11:00"), BookingStatus.CANCELLED); // not blocking

    assertThat(blockingCount(room.getId())).isEqualTo(2);
  }
}
