package com.sainisagar.auditlog.repository;

import com.sainisagar.auditlog.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findByArchivedFalseAndRecordedAtBeforeOrderBySequenceNumberAsc(Instant cutoff);
    List<AuditEvent> findByActorIdOrderBySequenceNumberAsc(String actorId);
    List<AuditEvent> findByResourceIdOrderBySequenceNumberAsc(String resourceId);
}
