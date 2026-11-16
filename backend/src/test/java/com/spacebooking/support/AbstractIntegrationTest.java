package com.spacebooking.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import com.spacebooking.user.infra.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real Postgres 16, full security chain, real HTTP. Seeder off, fixed test secret. */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = {
      "app.jwt.secret=" + AbstractIntegrationTest.TEST_SECRET,
      "app.seed.enabled=false",
      // tests call BookingScheduler.run() directly; a background run must never race them
      "app.scheduler.enabled=false",
      // off by default (prod); the contract-conformance tests read /v3/api-docs
      "springdoc.api-docs.enabled=true"
    })
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

  /** 45 bytes (>= 32 required by domain.md section 12). */
  public static final String TEST_SECRET = "integration-test-secret-0123456789-abcdefghij";

  public static final String PASSWORD = "Passw0rd!test";

  @ServiceConnection public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static {
    POSTGRES.start();
  }

  @LocalServerPort protected int port;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected UserRepository userRepository;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected MutableClock clock;

  @Autowired protected CacheManager cacheManager;

  @BeforeEach
  void resetClockBefore() {
    clock.reset();
  }

  /** The cache outlives a test, and several tests write rows with JDBC (bypassing eviction). */
  @BeforeEach
  void clearDailyScheduleCache() {
    Cache cache = cacheManager.getCache("dailySchedule");
    if (cache != null) {
      cache.clear();
    }
  }

  @AfterEach
  void resetClockAfter() {
    clock.reset();
  }

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  public record Resp(int status, String contentType, JsonNode body) {}

  public record TestUser(String email, String password) {}

  /** authorization: raw header value or null for none; body: String (raw JSON), other object (serialised) or null. */
  protected Resp call(String method, String path, String authorization, Object body) {
    try {
      HttpRequest.BodyPublisher publisher =
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(
                  body instanceof String s ? s : mapper.writeValueAsString(body), StandardCharsets.UTF_8);
      HttpRequest.Builder req =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).method(method, publisher);
      if (body != null) {
        req.header("Content-Type", "application/json");
      }
      if (authorization != null) {
        req.header("Authorization", authorization);
      }
      HttpResponse<String> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      String text = res.body();
      JsonNode json = text == null || text.isBlank() ? mapper.missingNode() : mapper.readTree(text);
      return new Resp(res.statusCode(), res.headers().firstValue("content-type").orElse(""), json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  protected Resp get(String path, String token) {
    return call("GET", path, token == null ? null : "Bearer " + token, null);
  }

  protected Resp post(String path, Object body) {
    return call("POST", path, null, body);
  }

  /** Status, ProblemDetail shape and stable code (testing.md: error assertions). */
  protected static void assertProblem(Resp r, int status, String code) {
    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(status);
    assertThat(r.contentType()).startsWith("application/problem+json");
    assertThat(r.body().path("status").asInt()).isEqualTo(status);
    assertThat(r.body().path("code").asText()).isEqualTo(code);
    assertThat(r.body().hasNonNull("title")).isTrue();
    assertThat(r.body().hasNonNull("type")).isTrue();
  }

  /** Persists a user through the repository (the only way to get MANAGER/ADMIN). */
  protected TestUser createUser(Role role) {
    String email = role.name().toLowerCase() + "." + UUID.randomUUID() + "@test.local";
    userRepository.save(new User(email, passwordEncoder.encode(PASSWORD), "Test " + role, role, java.time.Clock.systemUTC()));
    return new TestUser(email, PASSWORD);
  }

  protected String login(TestUser u) {
    Resp r = post("/api/v1/auth/login", java.util.Map.of("email", u.email(), "password", u.password()));
    assertThat(r.status()).as("login body: %s", r.body()).isEqualTo(200);
    return r.body().path("accessToken").asText();
  }

  protected String tokenFor(Role role) {
    return login(createUser(role));
  }

  protected String userId(String email) {
    return jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, email);
  }

  // ---- hand-made JWTs (library independent; HS256 over the raw UTF-8 secret bytes) ----

  protected static String b64(String s) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  protected static String hs256(String signingInput, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Correctly signed token with the given claims (sub, email, role, exp per openapi bearerAuth). */
  protected static String signedToken(String sub, String email, String role, Instant exp) {
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    String payload =
        b64(
            "{\"sub\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}"
                .formatted(sub, email, role, exp.getEpochSecond() - 3600, exp.getEpochSecond()));
    String input = header + "." + payload;
    return input + "." + hs256(input, TEST_SECRET);
  }

  /** Replaces the role claim in the payload and keeps header and signature untouched. */
  protected String withForgedRole(String token, String role) {
    try {
      String[] parts = token.split("\\.");
      ObjectNode claims = (ObjectNode) mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
      claims.put("role", role);
      return parts[0] + "." + b64(mapper.writeValueAsString(claims)) + "." + parts[2];
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
