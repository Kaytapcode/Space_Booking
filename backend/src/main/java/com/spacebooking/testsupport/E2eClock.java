package com.spacebooking.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** E2E-only clock: follows system time until pinned; always UTC. Registered only under profile e2e. */
public class E2eClock extends Clock {

    private final AtomicReference<Instant> pinned = new AtomicReference<>();

    public void set(Instant instant) {
        pinned.set(instant);
    }

    public Instant advance(long seconds) {
        return pinned.updateAndGet(p -> (p != null ? p : Instant.now()).plusSeconds(seconds));
    }

    public void reset() {
        pinned.set(null);
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
        Instant p = pinned.get();
        return p != null ? p : Instant.now();
    }
}
