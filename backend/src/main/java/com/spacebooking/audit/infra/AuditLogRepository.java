package com.spacebooking.audit.infra;

import com.spacebooking.audit.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByBookingIdOrderByCreatedAtAscIdAsc(UUID bookingId);
}
