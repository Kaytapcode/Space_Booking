package com.spacebooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** R46 (unit). Time is a fixed Clock; no sleeping. */
class JwtServiceTest {

  private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
  private static final String OTHER_SECRET = "another-secret-another-secret-0123456789xx";
  private static final Instant T0 = Instant.parse("2026-11-16T02:00:00Z");
  private static final Duration TTL = Duration.ofHours(1);

  private static JwtService service(String secret, Instant now) {
    return new JwtService(new JwtProperties(secret, TTL), Clock.fixed(now, ZoneOffset.UTC));
  }

  private static User user() {
    return new User("an@test.local", "$2a$10$hash", "An", Role.EMPLOYEE, Clock.systemUTC());
  }

  @Test
  void expired_rejected() {
    String token = service(SECRET, T0).issue(user());

    assertThat(service(SECRET, T0.plus(Duration.ofMinutes(59))).verify(token)).isPresent();
    assertThat(service(SECRET, T0.plus(Duration.ofMinutes(61))).verify(token)).isEmpty();
    assertThat(service(SECRET, T0.plus(Duration.ofDays(30))).verify(token)).isEmpty();
  }

  @Test
  void badSignature_rejected() {
    String token = service(SECRET, T0).issue(user());
    assertThat(service(SECRET, T0).verify(token)).isPresent();

    // signed with a different secret
    assertThat(service(OTHER_SECRET, T0).verify(token)).isEmpty();

    // first signature char flipped (the last char may carry only padding bits)
    int sig = token.lastIndexOf('.') + 1;
    char flipped = token.charAt(sig) == 'A' ? 'B' : 'A';
    String tampered = token.substring(0, sig) + flipped + token.substring(sig + 1);
    assertThat(service(SECRET, T0).verify(tampered)).isEmpty();
  }

  @Test
  void malformed_rejected() {
    JwtService s = service(SECRET, T0);
    assertThat(s.verify("")).isEmpty();
    assertThat(s.verify("not-a-jwt")).isEmpty();
    assertThat(s.verify("a.b.c")).isEmpty();
  }
}
