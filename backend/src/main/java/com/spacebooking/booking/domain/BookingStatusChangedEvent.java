package com.spacebooking.booking.domain;

import com.spacebooking.audit.domain.AuditAction;
import java.time.Instant;
import java.util.UUID;

/**
 * Observer event (domain.md section 8). {@code from} is null on CREATED. {@code details} carries the extra audit
 * value: the reject reason for REJECTED, the new ISO end time for CHECKED_OUT, otherwise null.
 */
public record BookingStatusChangedEvent(UUID bookingId, BookingStatus from, BookingStatus to, AuditAction action,
                                        String performedBy, String details, Instant occurredAt) {
}
