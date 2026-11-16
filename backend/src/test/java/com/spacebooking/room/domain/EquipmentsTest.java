package com.spacebooking.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R42 (domain.md section 11): DB column is sorted, unique, comma-joined, empty string when none. */
class EquipmentsTest {

  @Test
  void roundTrip_sortedCommaJoined() {
    String column = Equipments.toColumn(List.of("whiteboard", "tv", "video-conference"));

    assertThat(column).isEqualTo("tv,video-conference,whiteboard");
    assertThat(Equipments.fromColumn(column)).containsExactly("tv", "video-conference", "whiteboard");
  }

  @Test
  void toColumn_duplicatesRemoved() {
    assertThat(Equipments.toColumn(List.of("tv", "tv", "whiteboard", "tv"))).isEqualTo("tv,whiteboard");
  }

  @Test
  void empty_isEmptyString_andBack() {
    assertThat(Equipments.toColumn(List.of())).isEmpty();
    assertThat(Equipments.fromColumn("")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"tv, tv", "'tv,whiteboard', 'tv,whiteboard'", "'a-1,b-2,c', 'a-1,b-2,c'"})
  void fromColumn_toColumn_isIdentityOnNormalisedColumn(String column, String expected) {
    assertThat(Equipments.toColumn(Equipments.fromColumn(column))).isEqualTo(expected);
  }

  @Test
  void tvAndTvbox_stayDistinctTokens() {
    assertThat(Equipments.fromColumn(Equipments.toColumn(List.of("tvbox", "tv")))).containsExactly("tv", "tvbox");
  }
}
