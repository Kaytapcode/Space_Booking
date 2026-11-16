package com.spacebooking.audit.service;

import com.spacebooking.audit.api.AuditLogPage;
import com.spacebooking.audit.api.AuditLogResponse;
import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.audit.domain.AuditLog;
import com.spacebooking.audit.infra.AuditLogRepository;
import com.spacebooking.common.domain.ApiException;
import com.spacebooking.common.domain.ErrorCode;
import com.spacebooking.common.domain.FieldError;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AuditLogRepository logs;

    public AuditLogQueryService(AuditLogRepository logs) {
        this.logs = logs;
    }

    /** `from`/`to` are inclusive dates in the business timezone (domain.md section 1). */
    @Transactional(readOnly = true)
    public AuditLogPage search(UUID bookingId, AuditAction action, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed.",
                    List.of(new FieldError("from", "must not be after to")));
        }
        List<Specification<AuditLog>> parts = new ArrayList<>();
        if (bookingId != null) {
            parts.add((r, q, cb) -> cb.equal(r.get("bookingId"), bookingId));
        }
        if (action != null) {
            parts.add((r, q, cb) -> cb.equal(r.get("action"), action));
        }
        if (from != null) {
            LocalDateTime start = startOfDayUtc(from);
            parts.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), start));
        }
        if (to != null) {
            LocalDateTime end = startOfDayUtc(to.plusDays(1));
            parts.add((r, q, cb) -> cb.lessThan(r.get("createdAt"), end));
        }
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        return AuditLogPage.from(
                logs.findAll(Specification.allOf(parts), PageRequest.of(page, size, sort))
                        .map(AuditLogResponse::from));
    }

    private static LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
