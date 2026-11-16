package com.spacebooking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test-only clock: follows the system time until a test pins it with {@link #set}; always UTC. */
public class MutableClock extends Clock {

  private volatile Instant pinned;

  public void set(Instant instant) {
    this.pinned = instant;
  }

  public void advance(Duration d) {
    this.pinned = instant().plus(d);
  }

  public void reset() {
    this.pinned = null;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    Instant p = pinned;
    return p != null ? p : Instant.now();
  }
}
