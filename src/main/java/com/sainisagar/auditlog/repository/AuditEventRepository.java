package com.sainisagar.auditlog.repository;

import com.sainisagar.auditlog.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderBySequenceNumberDesc();
}
