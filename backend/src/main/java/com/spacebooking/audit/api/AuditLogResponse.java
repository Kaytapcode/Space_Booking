package com.spacebooking.audit.api;

import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.audit.domain.AuditLog;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** OpenAPI `AuditLogResponse`. */
public record AuditLogResponse(UUID id, UUID bookingId, AuditAction action, String performedBy, String details,
                               Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getBookingId(), log.getAction(), log.getPerformedBy(),
                log.getDetails(), log.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
