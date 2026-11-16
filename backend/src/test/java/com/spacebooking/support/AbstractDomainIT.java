package com.spacebooking.support;

import com.spacebooking.booking.domain.Booking;
import com.spacebooking.booking.domain.BookingStatus;
import com.spacebooking.booking.infra.BookingRepository;
import com.spacebooking.room.domain.Room;
import com.spacebooking.room.infra.RoomRepository;
import com.spacebooking.security.AuthenticatedUser;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;

/** Shared fixtures for the Phase 3 domain ITs. Every test creates its own rooms, so tests never conflict. */
public abstract class AbstractDomainIT extends AbstractIntegrationTest {

  protected static final String API = "/api/v1";
  protected static final String BOOKINGS = API + "/bookings";
  protected static final String ROOMS = API + "/rooms";

  @Autowired protected RoomRepository roomRepository;
  @Autowired protected BookingRepository bookingRepository;
  @Autowired protected DataSource dataSource;

  public record Actor(User user, String token) {
    public UUID id() {
      return user.getId();
    }

    public String email() {
      return user.getEmail();
    }

    public AuthenticatedUser principal() {
      return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }
  }

  protected Actor actor(Role role) {
    TestUser t = createUser(role);
    User u = userRepository.findByEmail(t.email()).orElseThrow();
    return new Actor(u, login(t));
  }

  // ---- time (real "now" truncated to the minute; tests that need other instants pin the clock) ----

  protected static Instant inMinutes(long minutes) {
    return Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(minutes, ChronoUnit.MINUTES);
  }

  protected static LocalDateTime utc(Instant i) {
    return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  // ---- fixtures ----

  protected static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  protected Room room(boolean requiresApproval) {
    return room(unique("Room"), 10, "", requiresApproval);
  }

  protected Room room(String name, int capacity, String equipments, boolean requiresApproval) {
    return roomRepository.save(new Room(name, "Tầng 1", capacity, equipments, requiresApproval));
  }

  protected Room inactiveRoom() {
    Room r = room(false);
    jdbc.update("UPDATE rooms SET is_active = FALSE WHERE id = ?", r.getId());
    return r;
  }

  protected Booking booking(Room room, Actor owner, Instant start, Instant end, BookingStatus status) {
    return booking(room, owner.user(), start, end, status);
  }

  protected Booking booking(Room room, User owner, Instant start, Instant end, BookingStatus status) {
    return bookingRepository.save(new Booking(room, owner, "Họp " + unique("t"), utc(start), utc(end), status, clock));
  }

  // ---- HTTP ----

  protected Resp as(Actor a, String method, String path, Object body) {
    return call(method, path, a == null ? null : "Bearer " + a.token(), body);
  }

  protected Resp get(Actor a, String path) {
    return as(a, "GET", path, null);
  }

  protected Resp patch(Actor a, String path) {
    return as(a, "PATCH", path, null);
  }

  protected Resp postJson(Actor a, String path, Object body) {
    return as(a, "POST", path, body);
  }

  protected static Map<String, Object> bookingBody(UUID roomId, Instant start, Instant end) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("roomId", roomId.toString());
    m.put("title", "Họp kế hoạch");
    m.put("startTime", start.toString());
    m.put("endTime", end.toString());
    return m;
  }

  protected static Map<String, Object> roomBody(String name, int capacity, List<String> equipments, boolean approval) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("location", "Tầng 2");
    m.put("capacity", capacity);
    m.put("equipments", equipments);
    m.put("requiresApproval", approval);
    return m;
  }

  // ---- concurrency ----

  /** Runs all tasks on their own threads, released together by a latch; returns results in task order. */
  protected static <T> List<T> parallel(List<Callable<T>> tasks) {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      if (!ready.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("threads did not start");
      }
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> f : futures) {
        results.add(f.get(60, TimeUnit.SECONDS));
      }
      return results;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      pool.shutdownNow();
    }
  }

  /** Waits until the request is blocked on a lock (pg_stat_activity); fails with its response if it finished instead. */
  protected void awaitBlockedOnLock(CompletableFuture<Resp> request) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(25))
        .until(
            () ->
                request.isDone()
                    || jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
                            Integer.class)
                        > 0);
    if (request.isDone()) {
      throw new AssertionError("request finished without waiting for the held lock: " + request.join().body());
    }
  }

  // ---- DB probes (independent of the code under test) ----

  protected List<Map<String, Object>> auditRows(UUID bookingId) {
    return jdbc.queryForList(
        "SELECT action, performed_by, details FROM audit_logs WHERE booking_id = ? ORDER BY created_at, id", bookingId);
  }

  protected String statusOf(UUID bookingId) {
    return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
  }

  protected int blockingCount(UUID roomId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM bookings WHERE room_id = ? AND status IN ('PENDING','CONFIRMED','CHECKED_IN')",
        Integer.class,
        roomId);
  }
}
