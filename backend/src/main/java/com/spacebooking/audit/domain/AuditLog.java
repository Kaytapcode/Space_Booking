package com.spacebooking.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "performed_by", nullable = false, updatable = false)
    private String performedBy;

    /** JSON text, see domain.md section 8. */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** {@code createdAt} is the instant the audited change happened (event time, from the injected Clock). */
    public AuditLog(UUID bookingId, AuditAction action, String performedBy, String details, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.bookingId = bookingId;
        this.action = action;
        this.performedBy = performedBy;
        this.details = details;
        this.createdAt = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
    }
}
