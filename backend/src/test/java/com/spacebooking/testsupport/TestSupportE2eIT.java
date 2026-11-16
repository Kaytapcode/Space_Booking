package com.spacebooking.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.infra.BookingRepository;
import com.spacebooking.room.domain.Room;
import com.spacebooking.room.infra.RoomRepository;
import com.spacebooking.support.AbstractIntegrationTest;
import com.spacebooking.user.infra.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Profile e2e: reset, clock and run-scheduler work end to end. Reuses the shared Postgres container only. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("e2e")
class TestSupportE2eIT {

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
    r.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
  }

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationContext context;
  @Autowired RoomRepository rooms;
  @Autowired UserRepository users;
  @Autowired BookingRepository bookings;
  @Autowired Clock clock;

  private ResponseEntity<String> post(String path, String json) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    return http.postForEntity(path, new HttpEntity<>(json, h), String.class);
  }

  @Test
  void beansPresentAndSharedClockDrivesEverything() {
    assertThat(context.getBeanNamesForType(TestSupportController.class)).hasSize(1);
    assertThat(clock).isInstanceOf(E2eClock.class);
    assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
  }

  @Test
  void resetRestoresSeedAndDropsEverythingElse() {
    assertThat(post("/test-support/reset", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    jdbc.update("UPDATE rooms SET is_active = FALSE, capacity = 99 WHERE name = 'Phòng Sen'");
    jdbc.update("INSERT INTO rooms (id, name, location, capacity, equipments, requires_approval) "
        + "VALUES (gen_random_uuid(), 'Extra', 'x', 3, '', false)");

    assertThat(post("/test-support/reset", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms", Integer.class)).isEqualTo(5);
    assertThat(jdbc.queryForObject(
            "SELECT is_active AND capacity = 6 FROM rooms WHERE name = 'Phòng Sen'", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(3);
  }

  @Test
  void clockPinsAdvancesResetsAndRejectsAmbiguousBodies() {
    ResponseEntity<String> pinned = post("/test-support/clock", "{\"instant\":\"2030-01-01T00:00:00Z\"}");
    assertThat(pinned.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(pinned.getBody()).contains("\"now\":\"2030-01-01T00:00:00Z\"");

    assertThat(post("/test-support/clock", "{\"advanceSeconds\":90}").getBody())
        .contains("\"now\":\"2030-01-01T00:01:30Z\"");

    assertThat(post("/test-support/clock", "{\"instant\":\"2030-01-01T00:00:00Z\",\"reset\":true}")
            .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(post("/test-support/clock", "{\"reset\":true}").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(Duration.between(clock.instant(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  void runSchedulerMarksNoShowAtClockStartPlus16Minutes() {
    post("/test-support/reset", null);
    Room room = rooms.findAll().get(0);
    var user = users.findByEmail("employee@spacebooking.local").orElseThrow();
    LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);
    Booking b = bookings.save(new Booking(room, user, "Họp", start, start.plusHours(1),
        BookingStatus.CONFIRMED, clock));

    post("/test-support/clock", "{\"instant\":\"" + start.plusMinutes(16).toInstant(ZoneOffset.UTC) + "\"}");
    assertThat(post("/test-support/run-scheduler", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, b.getId()))
        .isEqualTo("NO_SHOW");
    post("/test-support/reset", null);
  }
}
