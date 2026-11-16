package com.spacebooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.support.AbstractIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** R48 (domain.md section 12): CORS origins come from configuration; only those origins are served. */
@TestPropertySource(properties = "app.cors.allowed-origins=http://allowed.example")
class CorsIT extends AbstractIntegrationTest {

  private static final String ALLOWED = "http://allowed.example";
  private static final String OTHER = "http://evil.example";
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Test
  void allowedOrigin_ok_otherOrigin_rejected() throws Exception {
    HttpResponse<String> allowedPreflight = preflight(ALLOWED);
    assertThat(allowedPreflight.statusCode()).isEqualTo(200);
    assertThat(allowedPreflight.headers().firstValue("Access-Control-Allow-Origin")).contains(ALLOWED);
    assertThat(allowedPreflight.headers().firstValue("Access-Control-Allow-Methods")).hasValueSatisfying(m -> assertThat(m).contains("PATCH"));
    assertThat(allowedPreflight.headers().firstValue("Access-Control-Allow-Headers")).hasValueSatisfying(h -> assertThat(h.toLowerCase()).contains("authorization"));

    HttpResponse<String> otherPreflight = preflight(OTHER);
    assertThat(otherPreflight.statusCode()).isEqualTo(403);
    assertThat(otherPreflight.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

    // actual requests: the allowed origin gets the header on a normal (here 401) response, the other one is refused
    HttpResponse<String> allowedActual = actual(ALLOWED);
    assertThat(allowedActual.statusCode()).isEqualTo(401);
    assertThat(allowedActual.headers().firstValue("Access-Control-Allow-Origin")).contains(ALLOWED);

    HttpResponse<String> otherActual = actual(OTHER);
    assertThat(otherActual.statusCode()).isEqualTo(403);
    assertThat(otherActual.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  private HttpResponse<String> preflight(String origin) throws Exception {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/rooms"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "PATCH")
            .header("Access-Control-Request-Headers", "Authorization,Content-Type"));
  }

  private HttpResponse<String> actual(String origin) throws Exception {
    return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/me")).GET().header("Origin", origin));
  }

  private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
