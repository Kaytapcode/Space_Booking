package com.spacebooking.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.spacebooking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Default profile: no test-support bean and no open /test-support path (PLAN.md Phase 6). */
class TestSupportProfileIT extends AbstractIntegrationTest {

  @Autowired ApplicationContext context;

  @Test
  void testSupportBeansAbsentInDefaultProfile() {
    assertThat(context.getBeanNamesForType(TestSupportController.class)).isEmpty();
    assertThat(context.getBeanNamesForType(TestSupportService.class)).isEmpty();
    assertThat(context.getBeanNamesForType(E2eClock.class)).isEmpty();
  }

  @Test
  void testSupportPathsStayProtectedInDefaultProfile() {
    assertProblem(post("/test-support/reset", null), 401, "UNAUTHENTICATED");
    assertProblem(post("/test-support/clock", "{\"reset\":true}"), 401, "UNAUTHENTICATED");
    assertProblem(post("/test-support/run-scheduler", null), 401, "UNAUTHENTICATED");
  }
}
