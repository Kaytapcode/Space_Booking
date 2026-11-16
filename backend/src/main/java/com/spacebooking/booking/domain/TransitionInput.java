package com.spacebooking.booking.domain;

import java.time.Instant;

/** {@code reason} is null except for REJECT. */
public record TransitionInput(Instant now, String reason) {
}
