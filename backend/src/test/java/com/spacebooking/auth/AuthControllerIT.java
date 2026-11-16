package com.spacebooking.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacebooking.support.AbstractIntegrationTest;
import com.spacebooking.user.domain.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthControllerIT extends AbstractIntegrationTest {

  private static final String REGISTER = "/api/v1/auth/register";
  private static final String LOGIN = "/api/v1/auth/login";
  private static final String ME = "/api/v1/auth/me";

  private static String uniqueEmail() {
    return "user." + UUID.randomUUID() + "@test.local";
  }

  private static Map<String, Object> registerBody(String email) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("email", email);
    m.put("password", "Matkhau@2026");
    m.put("fullName", "Nguyễn Văn An");
    return m;
  }

  private int countUsers(String email) {
    return jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
  }

  // ---- A01 POST /auth/register ----

  @Test
  void register_201() {
    String email = uniqueEmail();
    Resp r = post(REGISTER, registerBody(email));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    JsonNode b = r.body();
    assertThat(b.path("accessToken").asText()).isNotBlank();
    assertThat(b.path("tokenType").asText()).isEqualTo("Bearer");
    assertThat(b.path("expiresIn").asInt()).isEqualTo(8 * 3600); // JWT_TTL default 8 h
    assertThat(b.path("user").path("id").asText()).isNotBlank();
    assertThat(b.path("user").path("email").asText()).isEqualTo(email);
    assertThat(b.path("user").path("fullName").asText()).isEqualTo("Nguyễn Văn An");
    assertThat(b.path("user").path("role").asText()).isEqualTo("EMPLOYEE");
    assertThat(Instant.parse(b.path("user").path("createdAt").asText())).isNotNull();
    assertThat(b.toString()).doesNotContain("password");
    // the returned token logs the user in
    assertThat(get(ME, b.path("accessToken").asText()).status()).isEqualTo(200);
  }

  static Stream<Arguments> invalidRegisterBodies() {
    String ok = "Matkhau@2026";
    List<Arguments> l = new ArrayList<>();
    l.add(Arguments.of("password 7 chars", Map.of("email", "a@test.local", "password", "1234567", "fullName", "An"), "password"));
    l.add(Arguments.of("password 73 chars", Map.of("email", "a@test.local", "password", "x".repeat(73), "fullName", "An"), "password"));
    l.add(Arguments.of("email malformed", Map.of("email", "not-an-email", "password", ok, "fullName", "An"), "email"));
    l.add(Arguments.of("email missing", Map.of("password", ok, "fullName", "An"), "email"));
    l.add(Arguments.of("password missing", Map.of("email", "a@test.local", "fullName", "An"), "password"));
    l.add(Arguments.of("fullName missing", Map.of("email", "a@test.local", "password", ok), "fullName"));
    l.add(Arguments.of("fullName empty", Map.of("email", "a@test.local", "password", ok, "fullName", ""), "fullName"));
    l.add(Arguments.of("fullName 101 chars", Map.of("email", "a@test.local", "password", ok, "fullName", "x".repeat(101)), "fullName"));
    return l.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRegisterBodies")
  void register_invalid_400(String label, Map<String, Object> body, String field) {
    Resp r = post(REGISTER, body);

    assertProblem(r, 400, "VALIDATION_FAILED");
    JsonNode errors = r.body().path("errors");
    assertThat(errors.isArray()).isTrue();
    assertThat(errors).isNotEmpty();
    assertThat(errors.findValuesAsText("field")).contains(field);
    errors.forEach(e -> assertThat(e.path("message").asText()).isNotBlank());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "MANAGER"})
  void register_roleInBody_400_noPrivilegedAccount(String role) {
    // RegisterRequest has additionalProperties: false, so an extra `role` is a request-shape error.
    String email = uniqueEmail();
    Map<String, Object> body = registerBody(email);
    body.put("role", role);

    Resp r = post(REGISTER, body);

    assertProblem(r, 400, "VALIDATION_FAILED");
    assertThat(countUsers(email)).isZero();
  }

  @Test
  void register_duplicate_409() {
    String email = uniqueEmail();
    assertThat(post(REGISTER, registerBody(email)).status()).isEqualTo(201);

    Resp r = post(REGISTER, registerBody(email));

    assertProblem(r, 409, "EMAIL_ALREADY_EXISTS");
    assertThat(countUsers(email)).isEqualTo(1);
  }

  // R43
  @Test
  void register_createsEmployee_lowercaseEmail() {
    String lower = uniqueEmail();
    Resp r = post(REGISTER, registerBody(lower.toUpperCase()));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(201);
    assertThat(r.body().path("user").path("role").asText()).isEqualTo("EMPLOYEE");
    assertThat(r.body().path("user").path("email").asText()).isEqualTo(lower);
    assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, lower)).isEqualTo("EMPLOYEE");
  }

  // R44
  @Test
  void register_storesBcryptHash() {
    String email = uniqueEmail();
    assertThat(post(REGISTER, registerBody(email)).status()).isEqualTo(201);

    String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);

    assertThat(hash).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$").doesNotContain("Matkhau@2026");
    assertThat(passwordEncoder.matches("Matkhau@2026", hash)).isTrue();
  }

  // R56
  @Test
  void register_upperCaseDuplicate_409() {
    String email = uniqueEmail();
    assertThat(post(REGISTER, registerBody(email)).status()).isEqualTo(201);

    Resp r = post(REGISTER, registerBody("  " + email.toUpperCase() + "  "));

    assertProblem(r, 409, "EMAIL_ALREADY_EXISTS");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email) = ?", Integer.class, email)).isEqualTo(1);
  }

  // ---- A02 POST /auth/login ----

  @Test
  void login_200() {
    TestUser u = createUser(Role.MANAGER);

    Resp r = post(LOGIN, Map.of("email", u.email(), "password", u.password()));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("accessToken").asText()).isNotBlank();
    assertThat(r.body().path("tokenType").asText()).isEqualTo("Bearer");
    assertThat(r.body().path("expiresIn").asInt()).isEqualTo(8 * 3600);
    assertThat(r.body().path("user").path("email").asText()).isEqualTo(u.email());
    assertThat(r.body().path("user").path("role").asText()).isEqualTo("MANAGER");
  }

  static Stream<Arguments> invalidLoginBodies() {
    return Stream.of(
        Arguments.of("email malformed", Map.of("email", "nope", "password", "x"), "email"),
        Arguments.of("email missing", Map.of("password", "x"), "email"),
        Arguments.of("password missing", Map.of("email", "a@test.local"), "password"),
        Arguments.of("password empty", Map.of("email", "a@test.local", "password", ""), "password"),
        Arguments.of("password 73 chars", Map.of("email", "a@test.local", "password", "x".repeat(73)), "password"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidLoginBodies")
  void login_invalid_400(String label, Map<String, Object> body, String field) {
    Resp r = post(LOGIN, body);

    assertProblem(r, 400, "VALIDATION_FAILED");
    assertThat(r.body().path("errors").findValuesAsText("field")).contains(field);
  }

  @Test
  void login_badCredentials_401() {
    TestUser u = createUser(Role.EMPLOYEE);

    Resp r = post(LOGIN, Map.of("email", u.email(), "password", "WrongPassword1"));

    assertProblem(r, 401, "INVALID_CREDENTIALS");
  }

  // R45
  @Test
  void login_wrongPassword_and_unknownEmail_sameProblem() {
    TestUser u = createUser(Role.EMPLOYEE);

    Resp wrongPassword = post(LOGIN, Map.of("email", u.email(), "password", "WrongPassword1"));
    Resp unknownEmail = post(LOGIN, Map.of("email", uniqueEmail(), "password", "WrongPassword1"));

    assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
    assertProblem(unknownEmail, 401, "INVALID_CREDENTIALS");
    assertThat(unknownEmail.body()).isEqualTo(wrongPassword.body());
  }

  // R62 (D33): public operations ignore Authorization
  @Test
  void login_withExpiredToken_200() {
    TestUser u = createUser(Role.EMPLOYEE);
    String expired =
        signedToken(userId(u.email()), u.email(), "EMPLOYEE", Instant.now().minus(1, ChronoUnit.DAYS));

    for (String authorization : List.of("Bearer " + expired, "Bearer garbage", "Basic Zm9vOmJhcg==")) {
      Resp r = call("POST", LOGIN, authorization, Map.of("email", u.email(), "password", u.password()));
      assertThat(r.status()).as("Authorization: %s, body: %s", authorization, r.body()).isEqualTo(200);
      assertThat(r.body().path("accessToken").asText()).isNotBlank();
    }
  }

  // ---- A03 GET /auth/me ----

  @Test
  void me_200() {
    TestUser u = createUser(Role.ADMIN);

    Resp r = get(ME, login(u));

    assertThat(r.status()).as("body: %s", r.body()).isEqualTo(200);
    assertThat(r.body().path("id").asText()).isEqualTo(userId(u.email()));
    assertThat(r.body().path("email").asText()).isEqualTo(u.email());
    assertThat(r.body().path("fullName").asText()).isEqualTo("Test ADMIN");
    assertThat(r.body().path("role").asText()).isEqualTo("ADMIN");
    assertThat(Instant.parse(r.body().path("createdAt").asText())).isNotNull();
    assertThat(r.body().toString()).doesNotContain("password");
  }

  @Test
  void me_noToken_401() {
    assertProblem(get(ME, null), 401, "UNAUTHENTICATED");
  }

  // ---- gate extras: every error body is a ProblemDetail with code ----

  @Test
  void login_malformedJson_400() {
    assertProblem(call("POST", LOGIN, null, "{not json"), 400, "VALIDATION_FAILED");
  }

  @Test
  void login_wrongMethod_405() {
    assertProblem(call("GET", LOGIN, null, null), 405, "REQUEST_NOT_SUPPORTED");
  }
}
