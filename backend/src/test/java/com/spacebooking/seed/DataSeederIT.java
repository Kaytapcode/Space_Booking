package com.spacebooking.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.spacebooking.support.AbstractIntegrationTest;
import com.spacebooking.support.AppLauncher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * R49 (domain.md section 13). The class context has the seeder enabled with explicit passwords (as in prod);
 * the disabled case boots a second application. Seed rows are removed around every test because the
 * Postgres container is shared with the other integration tests.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = {
      "app.jwt.secret=" + AbstractIntegrationTest.TEST_SECRET,
      "app.seed.enabled=true",
      "app.seed.admin-password=Seed-Admin-1234",
      "app.seed.manager-password=Seed-Manager-1234",
      "app.seed.employee-password=Seed-Employee-1234"
    })
class DataSeederIT extends AbstractIntegrationTest {

  private static final List<String> EMAILS =
      List.of("admin@spacebooking.local", "manager@spacebooking.local", "employee@spacebooking.local");
  private static final List<String> ROOMS =
      List.of("Phòng Sen", "Phòng Mai", "Phòng Cúc", "Phòng Trường Sơn", "Hội trường Bạch Đằng");

  @Autowired DataSeeder seeder;

  @BeforeEach
  @AfterEach
  void cleanSeedRows() {
    jdbc.update("DELETE FROM rooms WHERE name = ANY (?)", (Object) ROOMS.toArray(String[]::new));
    jdbc.update("DELETE FROM users WHERE email = ANY (?)", (Object) EMAILS.toArray(String[]::new));
  }

  private int seededUsers() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE email = ANY (?)", Integer.class, (Object) EMAILS.toArray(String[]::new));
  }

  private int seededRooms() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM rooms WHERE name = ANY (?)", Integer.class, (Object) ROOMS.toArray(String[]::new));
  }

  @Test
  void disabled_seedsNothing() {
    try (ConfigurableApplicationContext ctx =
        AppLauncher.run("--app.jwt.secret=" + TEST_SECRET, "--app.seed.enabled=false")) {
      assertThat(ctx.isRunning()).isTrue();
    }

    assertThat(seededUsers()).isZero();
    assertThat(seededRooms()).isZero();
  }

  @Test
  void enabled_seedsUsersAndRooms() {
    seeder.seed();

    assertThat(seededUsers()).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = 'admin@spacebooking.local'", String.class))
        .isEqualTo("ADMIN");
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = 'manager@spacebooking.local'", String.class))
        .isEqualTo("MANAGER");
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = 'employee@spacebooking.local'", String.class))
        .isEqualTo("EMPLOYEE");
    assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE email = 'employee@spacebooking.local'", String.class))
        .isEqualTo("Trần Thị Bình");

    assertThat(seededRooms()).isEqualTo(5);
    Map<String, Object> hall = jdbc.queryForMap("SELECT * FROM rooms WHERE name = 'Hội trường Bạch Đằng'");
    assertThat(hall.get("location")).isEqualTo("Tầng 1");
    assertThat(hall.get("capacity")).isEqualTo(40);
    assertThat(hall.get("equipments")).isEqualTo("microphone,projector,speaker");
    assertThat(hall.get("requires_approval")).isEqualTo(true);
    Map<String, Object> sen = jdbc.queryForMap("SELECT * FROM rooms WHERE name = 'Phòng Sen'");
    assertThat(sen.get("capacity")).isEqualTo(6);
    assertThat(sen.get("equipments")).isEqualTo("tv,whiteboard");
    assertThat(sen.get("requires_approval")).isEqualTo(false);

    // prod passwords come from app.seed.*-password
    assertThat(post("/api/v1/auth/login", Map.of("email", EMAILS.get(0), "password", "Seed-Admin-1234")).status())
        .isEqualTo(200);
    assertThat(post("/api/v1/auth/login", Map.of("email", EMAILS.get(1), "password", "Seed-Manager-1234")).status())
        .isEqualTo(200);
    assertThat(post("/api/v1/auth/login", Map.of("email", EMAILS.get(2), "password", "Seed-Employee-1234")).status())
        .isEqualTo(200);
  }

  @Test
  void runTwice_noDuplicates() {
    seeder.seed();
    seeder.seed();

    assertThat(seededUsers()).isEqualTo(3);
    assertThat(seededRooms()).isEqualTo(5);
  }
}
