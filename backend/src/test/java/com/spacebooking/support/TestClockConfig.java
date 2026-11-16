package com.spacebooking.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the production Clock for every integration test (testing.md: time is controlled via Clock). */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }
}
