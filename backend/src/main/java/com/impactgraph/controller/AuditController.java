package com.impactgraph.controller;

import com.impactgraph.entity.AuditEvent;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Audit log endpoints. All require ADMIN role.
 * <p>
 * Audit records are read-only via the API. There are no update or delete endpoints.
 * This prevents any user (including admins via API) from tampering with audit trails.
 * </p>
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * List all audit events, paginated, newest first.
     */
    @GetMapping("/events")
    public ResponseEntity<Page<AuditEvent>> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100); // Cap page size
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditService.getEvents(pageable));
    }

    /**
     * Filter audit events by project.
     */
    @GetMapping("/events/by-project/{projectId}")
    public ResponseEntity<Page<AuditEvent>> getEventsByProject(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditService.getEventsByProject(projectId, pageable));
    }

    /**
     * Filter audit events by type.
     */
    @GetMapping("/events/by-type/{eventType}")
    public ResponseEntity<Page<AuditEvent>> getEventsByType(
            @PathVariable AuditEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditService.getEventsByType(eventType, pageable));
    }
}
