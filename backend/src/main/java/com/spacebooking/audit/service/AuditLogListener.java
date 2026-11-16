package com.spacebooking.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.audit.domain.AuditLog;
import com.spacebooking.audit.infra.AuditLogRepository;
import com.spacebooking.booking.domain.BookingStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Observer: synchronous, so the audit row commits or rolls back with the state change (domain.md section 8). */
@Component
public class AuditLogListener {

    private final AuditLogRepository logs;
    private final ObjectMapper mapper;

    public AuditLogListener(AuditLogRepository logs, ObjectMapper mapper) {
        this.logs = logs;
        this.mapper = mapper;
    }

    @EventListener
    public void on(BookingStatusChangedEvent e) {
        logs.save(new AuditLog(e.bookingId(), e.action(), e.performedBy(), details(e), e.occurredAt()));
    }

    String details(BookingStatusChangedEvent e) {
        // A tree node always writes explicit nulls, whatever the global Jackson inclusion setting is.
        ObjectNode node = mapper.createObjectNode();
        node.put("from", e.from() == null ? null : e.from().name());
        node.put("to", e.to().name());
        if (e.action() == AuditAction.REJECTED) {
            node.put("reason", e.details());
        } else if (e.action() == AuditAction.CHECKED_OUT) {
            node.put("endTime", e.details());
        }
        return node.toString();
    }
}
