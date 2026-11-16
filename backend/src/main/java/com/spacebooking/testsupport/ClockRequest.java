package com.spacebooking.testsupport;

import java.time.Instant;

/** Exactly one field is meaningful: instant (pin), advanceSeconds (move), reset=true (back to system time). */
public record ClockRequest(Instant instant, Long advanceSeconds, Boolean reset) {}
