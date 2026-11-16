package com.spacebooking.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.room.domain.Room;
import com.spacebooking.room.infra.RoomSpecifications;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.domain.Specification;

/** R36, R37 (domain.md section 11). */
class RoomSpecificationIT extends AbstractDomainIT {

  private static final Instant T10 = Instant.parse("2031-03-10T10:00:00Z");

  private String prefix;

  @BeforeEach
  void setUp() {
    prefix = unique("RS");
  }

  private Room room(String suffix, int capacity, String equipments) {
    return room(prefix + "-" + suffix, capacity, equipments, false);
  }

  private List<String> suffixes(Specification<Room> spec) {
    return roomRepository.findAll(spec).stream()
        .map(Room::getName)
        .filter(n -> n.startsWith(prefix + "-"))
        .map(n -> n.substring(prefix.length() + 1))
        .sorted()
        .toList();
  }

  private List<String> search(Integer minCapacity, List<String> equipments) {
    return suffixes(RoomSpecifications.search(minCapacity, equipments, null, null, false));
  }

  private void equipmentRooms() {
    room("A", 6, "tv,whiteboard");
    room("B", 6, "tvbox");
    room("C", 6, "tv");
    room("D", 6, "");
    room("E", 6, "video-conference,whiteboard");
  }

  static Stream<Arguments> equipmentCases() {
    return Stream.of(
        Arguments.of("tv excludes tvbox", List.of("tv"), List.of("A", "C")),
        Arguments.of("tvbox only matches tvbox", List.of("tvbox"), List.of("B")),
        Arguments.of("tv+whiteboard requires both", List.of("tv", "whiteboard"), List.of("A")),
        Arguments.of("whiteboard", List.of("whiteboard"), List.of("A", "E")),
        Arguments.of("hyphenated token", List.of("video-conference"), List.of("E")),
        Arguments.of("tv+tvbox requires both", List.of("tv", "tvbox"), List.of()),
        Arguments.of("prefix of a token does not match", List.of("white"), List.of()),
        Arguments.of("suffix of a token does not match", List.of("conference"), List.of()),
        Arguments.of("unknown token", List.of("projector"), List.of()),
        Arguments.of("empty list = no filter", List.of(), List.of("A", "B", "C", "D", "E")));
  }

  // R36
  @ParameterizedTest(name = "{0}")
  @MethodSource("equipmentCases")
  void equipments(String name, List<String> requested, List<String> expected) {
    equipmentRooms();

    assertThat(search(null, requested)).isEqualTo(expected);
  }

  @Test
  void equipments_nullList_noFilter() {
    equipmentRooms();

    assertThat(search(null, null)).containsExactly("A", "B", "C", "D", "E");
  }

  // R37
  @Test
  void minCapacity() {
    room("A", 4, "");
    room("B", 6, "");
    room("C", 12, "");

    assertThat(search(6, null)).containsExactly("B", "C");
    assertThat(search(4, null)).as("inclusive").containsExactly("A", "B", "C");
    assertThat(search(7, null)).containsExactly("C");
    assertThat(search(13, null)).isEmpty();
    assertThat(search(null, null)).containsExactly("A", "B", "C");
  }

  @Test
  void filters_combineWithAnd() {
    room("A", 12, "tv,whiteboard");
    room("B", 4, "tv,whiteboard");
    room("C", 12, "tv");

    assertThat(search(6, List.of("tv", "whiteboard"))).containsExactly("A");
  }

  private List<String> available(String from, String to) {
    return suffixes(
        RoomSpecifications.search(
            null, null, LocalDateTime.parse("2031-03-10T" + from), LocalDateTime.parse("2031-03-10T" + to), false));
  }

  // R37
  @Test
  void availability_excludesBlocked() {
    Actor owner = actor(Role.EMPLOYEE);
    for (BookingStatus status : BookingStatus.values()) {
      booking(room(status.name(), 6, ""), owner, T10, T10.plusSeconds(3600), status);
    }
    room("FREE", 6, "");

    assertThat(available("10:30:00", "11:30:00")).containsExactly("CANCELLED", "COMPLETED", "FREE", "NO_SHOW");
    assertThat(available("09:00:00", "11:30:00")).as("containing").containsExactly("CANCELLED", "COMPLETED", "FREE", "NO_SHOW");
    assertThat(available("10:15:00", "10:45:00")).as("contained").containsExactly("CANCELLED", "COMPLETED", "FREE", "NO_SHOW");
    assertThat(available("09:00:00", "10:01:00")).as("1-minute overlap").containsExactly("CANCELLED", "COMPLETED", "FREE", "NO_SHOW");
  }

  // R37
  @Test
  void availability_adjacentIncluded() {
    booking(room("A", 6, ""), actor(Role.EMPLOYEE), T10, T10.plusSeconds(3600), BookingStatus.CONFIRMED);

    assertThat(available("09:00:00", "10:00:00")).as("ends when the booking starts").containsExactly("A");
    assertThat(available("11:00:00", "12:00:00")).as("starts when the booking ends").containsExactly("A");
    assertThat(available("09:00:00", "10:01:00")).isEmpty();
  }

  @Test
  void includeInactive() {
    room("A", 6, "");
    Room inactive = room("B", 6, "");
    jdbc.update("UPDATE rooms SET is_active = FALSE WHERE id = ?", inactive.getId());

    assertThat(suffixes(RoomSpecifications.search(null, null, null, null, false))).containsExactly("A");
    assertThat(suffixes(RoomSpecifications.search(null, null, null, null, true))).containsExactly("A", "B");
  }
}
