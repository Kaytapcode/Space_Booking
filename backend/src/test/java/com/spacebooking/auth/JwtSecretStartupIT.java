package com.spacebooking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spacebooking.support.AbstractIntegrationTest;
import com.spacebooking.support.AppLauncher;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * R47: outside the dev/e2e profiles the app needs a JWT secret of at least 32 bytes.
 * Arguments are command-line args so a JWT_SECRET env var on the host cannot mask the case.
 */
class JwtSecretStartupIT {

  private static final String NO_SEED = "--app.seed.enabled=false";

  @Test
  void prodWithoutSecret_failsToStart() {
    assertThatThrownBy(() -> AppLauncher.run("--app.jwt.secret=", NO_SEED))
        .satisfies(JwtSecretStartupIT::assertCausedBySecret);
  }

  @ParameterizedTest
  @ValueSource(strings = {"short", "0123456789012345678901234567890"}) // 5 and 31 bytes
  void prodWithTooShortSecret_failsToStart(String secret) {
    assertThatThrownBy(() -> AppLauncher.run("--app.jwt.secret=" + secret, NO_SEED))
        .satisfies(JwtSecretStartupIT::assertCausedBySecret);
  }

  /** Control: same launch parameters with a 32-byte secret start, so the failures above are about the secret. */
  @Test
  void prodWithValidSecret_starts() {
    try (ConfigurableApplicationContext ctx =
        AppLauncher.run("--app.jwt.secret=01234567890123456789012345678901", NO_SEED)) {
      assertThat(ctx.isRunning()).isTrue();
    }
  }

  private static void assertCausedBySecret(Throwable t) {
    StringBuilder chain = new StringBuilder();
    for (Throwable c = t; c != null; c = c.getCause()) {
      chain.append(c.getMessage()).append('\n');
    }
    assertThat(chain.toString().toLowerCase(Locale.ROOT)).contains("secret");
  }

  static {
    // make the shared container start before any AppLauncher call
    assertThat(AbstractIntegrationTest.POSTGRES.isRunning()).isTrue();
  }
}
