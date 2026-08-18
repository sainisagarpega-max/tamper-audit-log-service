package com.sainisagar.auditlog.service;

import com.sainisagar.auditlog.dto.RetentionResponse;
import com.sainisagar.auditlog.entity.AuditEvent;
import com.sainisagar.auditlog.repository.AuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RetentionService {

    private final AuditEventRepository repository;
    private final long retentionDays;
    private final Clock clock = Clock.systemUTC();

    public RetentionService(AuditEventRepository repository,
                            @Value("${app.retention.days}") long retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public RetentionResponse archiveExpired() {
        Instant completedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = completedAt.minus(retentionDays, ChronoUnit.DAYS);
        List<AuditEvent> expired = repository
                .findByArchivedFalseAndRecordedAtBeforeOrderBySequenceNumberAsc(cutoff);
        expired.forEach(event -> event.archive(completedAt));
        return new RetentionResponse(expired.size(), cutoff, completedAt);
    }

    @Scheduled(cron = "${app.retention.schedule}", zone = "UTC")
    @Transactional
    public void scheduledArchive() {
        archiveExpired();
    }
}
