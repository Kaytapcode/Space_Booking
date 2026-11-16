package com.spacebooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** R01: new.start < old.end AND new.end > old.start (domain.md section 2). Existing slot is always 10:00-11:00. */
class OverlapPolicyTest {

  private static Instant at(String hhmm) {
    return Instant.parse("2026-11-17T" + hhmm + ":00Z");
  }

  @ParameterizedTest(name = "{0}: new {1}-{2} -> overlaps={3}")
  @CsvSource({
    "identical,            10:00, 11:00, true",
    "contained,            10:15, 10:45, true",
    "containing,           09:00, 12:00, true",
    "overlap left,         09:30, 10:30, true",
    "overlap right,        10:30, 11:30, true",
    "adjacent before,      09:00, 10:00, false",
    "adjacent after,       11:00, 12:00, false",
    "1-minute overlap left, 09:00, 10:01, true",
    "1-minute overlap right,10:59, 12:00, true",
    "disjoint before,      08:00, 09:00, false",
    "disjoint after,       12:00, 13:00, false",
    "same start shorter,   10:00, 10:30, true",
    "same end shorter,     10:30, 11:00, true",
    "1-minute booking inside, 10:30, 10:31, true",
    "1-minute booking adjacent before, 09:59, 10:00, false",
    "1-minute booking adjacent after,  11:00, 11:01, false"
  })
  void overlaps(String name, String newStart, String newEnd, boolean expected) {
    Instant oldStart = at("10:00");
    Instant oldEnd = at("11:00");

    assertThat(OverlapPolicy.overlaps(at(newStart.trim()), at(newEnd.trim()), oldStart, oldEnd))
        .as(name)
        .isEqualTo(expected);
    // the predicate is symmetric
    assertThat(OverlapPolicy.overlaps(oldStart, oldEnd, at(newStart.trim()), at(newEnd.trim())))
        .as(name + " (swapped)")
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "sub-minute precision: {0}")
  @CsvSource({"2026-11-17T10:59:59Z, true", "2026-11-17T11:00:00Z, false"})
  void overlaps_endBoundaryIsExclusive(String newStart, boolean expected) {
    Instant start = Instant.parse(newStart);

    assertThat(OverlapPolicy.overlaps(start, start.plusSeconds(3600), at("10:00"), at("11:00"))).isEqualTo(expected);
  }
}
