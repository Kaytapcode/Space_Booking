package com.spacebooking.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.support.AbstractIntegrationTest;
import com.spacebooking.support.AppLauncher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * springdoc is off unless `springdoc.api-docs.enabled` is set (prod default): with only the default configuration
 * (no override passed) /v3/api-docs must not be served. AbstractIntegrationTest enables it, so this boots its own context.
 */
class ApiDocsDisabledIT {

  static {
    // make the shared container start before any AppLauncher call
    assertThat(AbstractIntegrationTest.POSTGRES.isRunning()).isTrue();
  }

  @Test
  void defaultConfig_apiDocsNotServed() throws Exception {
    try (ConfigurableApplicationContext ctx =
        AppLauncher.run("--app.jwt.secret=01234567890123456789012345678901", "--app.seed.enabled=false", "--app.scheduler.enabled=false")) {
      String port = ctx.getEnvironment().getProperty("local.server.port");
      HttpResponse<String> res =
          HttpClient.newHttpClient()
              .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertThat(res.statusCode()).as("body: %s", res.body()).isEqualTo(404);
      assertThat(res.body()).doesNotContain("openapi");
    }
  }
}
