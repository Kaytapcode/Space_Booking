package com.spacebooking.support;

import com.spacebooking.SpaceBookingApplication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Boots a throw-away application context against the shared Postgres container. Args win over env vars. */
public final class AppLauncher {

  private AppLauncher() {}

  public static ConfigurableApplicationContext run(String... extraArgs) {
    List<String> args = new ArrayList<>();
    args.add("--server.port=0");
    args.add("--spring.datasource.url=" + AbstractIntegrationTest.POSTGRES.getJdbcUrl());
    args.add("--spring.datasource.username=" + AbstractIntegrationTest.POSTGRES.getUsername());
    args.add("--spring.datasource.password=" + AbstractIntegrationTest.POSTGRES.getPassword());
    args.addAll(List.of(extraArgs));
    return SpringApplication.run(SpaceBookingApplication.class, args.toArray(String[]::new));
  }
}
