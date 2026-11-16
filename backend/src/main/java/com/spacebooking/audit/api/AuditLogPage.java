package com.spacebooking.audit.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** OpenAPI `AuditLogPage` (PageMetadata + content). */
public record AuditLogPage(int page, int size, long totalElements, int totalPages, List<AuditLogResponse> content) {

    public static AuditLogPage from(Page<AuditLogResponse> page) {
        return new AuditLogPage(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getContent());
    }
}
