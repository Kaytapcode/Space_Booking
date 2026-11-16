package com.spacebooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.booking.api.BookingPage;
import com.spacebooking.booking.api.DailyScheduleResponse;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.service.BookingService;
import com.spacebooking.room.domain.Room;
import com.spacebooking.security.AuthenticatedUser;
import com.spacebooking.support.AbstractDomainIT;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * R33 (domain.md section 10): the number of prepared statements does not grow with the number of bookings.
 * Own context (statistics on), accepted cost of a separate Spring context.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryCountIT extends AbstractDomainIT {

  private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired private BookingService bookingService;
  @Autowired private EntityManagerFactory emf;

  private static Instant local(LocalDate day, int hour) {
    return LocalDateTime.of(day, LocalTime.of(hour, 0)).atZone(HCM).toInstant();
  }

  /** One distinct room and one distinct owner per booking, so any per-row lazy load shows up as extra statements. */
  private List<Room> rooms(String prefix, int n) {
    List<Room> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(room(prefix + "-" + i, 8, "", false));
    }
    return out;
  }

  private List<User> users(int n) {
    String hash = passwordEncoder.encode(PASSWORD); // hashed once; the users never log in
    List<User> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(userRepository.save(new User("q" + i + "." + java.util.UUID.randomUUID() + "@test.local", hash, "Query " + i, Role.EMPLOYEE, clock)));
    }
    return out;
  }

  private Statistics statistics() {
    Statistics s = emf.unwrap(SessionFactory.class).getStatistics();
    assertThat(s.isStatisticsEnabled()).as("hibernate.generate_statistics").isTrue();
    return s;
  }

  private long statements(Runnable call) {
    cacheManager.getCache("dailySchedule").clear();
    Statistics s = statistics();
    s.clear();
    call.run();
    return s.getPrepareStatementCount();
  }

  // R33
  @Test
  void dailySchedule_constantQueries() {
    String prefix = unique("QC");
    List<Room> rooms = rooms(prefix, 50);
    List<User> users = users(50);
    LocalDate small = LocalDate.of(2033, 5, 10);
    LocalDate large = LocalDate.of(2033, 5, 11);
    for (int i = 0; i < 50; i++) {
      if (i < 5) {
        booking(rooms.get(i), users.get(i), local(small, 9), local(small, 10), BookingStatus.CONFIRMED);
      }
      booking(rooms.get(i), users.get(i), local(large, 9), local(large, 10), BookingStatus.CONFIRMED);
    }
    bookingService.dailySchedule(LocalDate.of(2033, 5, 12)); // warm-up: first use may prepare one-off statements

    long[] seen = new long[2];
    int[] bookings = new int[2];
    seen[0] = statements(() -> bookings[0] = countIn(bookingService.dailySchedule(small), prefix));
    seen[1] = statements(() -> bookings[1] = countIn(bookingService.dailySchedule(large), prefix));

    assertThat(bookings).containsExactly(5, 50);
    assertThat(seen[0]).as("statements for 5 vs 50 bookings").isPositive().isEqualTo(seen[1]);
  }

  private static int countIn(DailyScheduleResponse schedule, String prefix) {
    return schedule.rooms().stream()
        .filter(r -> r.room().name().startsWith(prefix))
        .mapToInt(r -> r.bookings().size())
        .sum();
  }

  // R33
  @Test
  void myBookings_constantQueries() {
    List<Room> rooms = rooms(unique("QM"), 50);
    List<User> users = users(2);
    LocalDate day = LocalDate.of(2033, 6, 10);
    for (int i = 0; i < 50; i++) {
      if (i < 5) {
        booking(rooms.get(i), users.get(0), local(day, 9), local(day, 10), BookingStatus.CONFIRMED);
      }
      booking(rooms.get(i), users.get(1), local(day, 11), local(day, 12), BookingStatus.CONFIRMED);
    }
    AuthenticatedUser few = new AuthenticatedUser(users.get(0).getId(), users.get(0).getEmail(), Role.EMPLOYEE);
    AuthenticatedUser many = new AuthenticatedUser(users.get(1).getId(), users.get(1).getEmail(), Role.EMPLOYEE);
    bookingService.myBookings(few, null, 0, 100); // warm-up

    BookingPage[] pages = new BookingPage[2];
    long fewStatements = statements(() -> pages[0] = bookingService.myBookings(few, null, 0, 100));
    long manyStatements = statements(() -> pages[1] = bookingService.myBookings(many, null, 0, 100));

    assertThat(pages[0].content()).hasSize(5);
    assertThat(pages[1].content()).hasSize(50);
    assertThat(fewStatements).as("statements for 5 vs 50 bookings").isPositive().isEqualTo(manyStatements);
  }
}
