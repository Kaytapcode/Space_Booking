package com.spacebooking.audit.api;

import static com.spacebooking.common.domain.ErrorCode.ACCESS_DENIED;
import static com.spacebooking.common.domain.ErrorCode.UNAUTHENTICATED;
import static com.spacebooking.common.domain.ErrorCode.VALIDATION_FAILED;

import com.spacebooking.audit.domain.AuditAction;
import com.spacebooking.audit.service.AuditLogQueryService;
import com.spacebooking.common.api.ApiErrors;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService audit;

    public AuditLogController(AuditLogQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    @ApiErrors({VALIDATION_FAILED, UNAUTHENTICATED, ACCESS_DENIED})
    @PreAuthorize("hasRole('ADMIN')")
    public AuditLogPage search(
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audit.search(bookingId, action, from, to, page, size);
    }
}
