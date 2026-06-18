package com.shopbilling.service;

import com.shopbilling.model.AuditLog;
import com.shopbilling.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogs;

    public AuditLogService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public void record(String actor, String action, String entityType, Long entityId, String note) {
        AuditLog log = new AuditLog();
        log.setActor(actor == null || actor.isBlank() ? "system" : actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setNote(note);
        auditLogs.save(log);
    }
}
