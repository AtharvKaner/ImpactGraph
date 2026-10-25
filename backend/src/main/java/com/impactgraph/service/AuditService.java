package com.impactgraph.service;

import com.impactgraph.entity.AuditEvent;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit event recording service.
 * <p>
 * All audit writes use REQUIRES_NEW propagation so they commit independently
 * of the business transaction (an audit record should persist even if the
 * business operation rolls back).
 * </p>
 * <p>
 * Security rules:
 * - Never log passwords, tokens, or secrets in the details field.
 * - Sanitize and truncate client-supplied metadata.
 * - Audit records are immutable via the API (no update/delete endpoints).
 * </p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Record an audit event.
     *
     * @param eventType  the type of event
     * @param actorId    the user ID of the actor, or null for anonymous events
     * @param actorEmail the actor's email at the time of the event
     * @param projectId  the project ID, if applicable
     * @param resourceId the resource ID, if applicable
     * @param outcome    "SUCCESS" or "FAILURE"
     * @param details    sanitized description (no credentials, truncated to 500 chars)
     * @param ipAddress  client IP address
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType eventType,
                         UUID actorId,
                         String actorEmail,
                         UUID projectId,
                         UUID resourceId,
                         String outcome,
                         String details,
                         String ipAddress) {
        try {
            AuditEvent event = new AuditEvent();
            event.setTimestamp(Instant.now());
            event.setEventType(eventType);
            event.setActorId(actorId);
            event.setActorEmail(sanitize(actorEmail));
            event.setProjectId(projectId);
            event.setResourceId(resourceId);
            event.setOutcome(outcome);
            event.setDetails(sanitize(details));
            event.setIpAddress(sanitize(ipAddress));

            auditEventRepository.save(event);
        } catch (Exception e) {
            // Audit failures must never break application flow
            log.error("Failed to write audit event: {}", eventType, e);
        }
    }

    /**
     * Retrieve paginated audit events. Admin-only access enforced by controller.
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> getEvents(Pageable pageable) {
        return auditEventRepository.findByOrderByTimestampDesc(pageable);
    }

    /**
     * Retrieve audit events for a specific project.
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> getEventsByProject(UUID projectId, Pageable pageable) {
        return auditEventRepository.findByProjectIdOrderByTimestampDesc(projectId, pageable);
    }

    /**
     * Retrieve audit events by type.
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> getEventsByType(AuditEventType eventType, Pageable pageable) {
        return auditEventRepository.findByEventTypeOrderByTimestampDesc(eventType, pageable);
    }

    /**
     * Retrieve events for a specific actor (for user's own events in transparency view).
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> getEventsByActor(UUID actorId, Pageable pageable) {
        return auditEventRepository.findByActorIdOrderByTimestampDesc(actorId, pageable);
    }

    /**
     * Sanitize a string: strip control characters, truncate.
     */
    private String sanitize(String input) {
        if (input == null) return null;
        // Remove control characters except space
        String cleaned = input.replaceAll("[\\p{Cc}&&[^\\s]]", "");
        if (cleaned.length() > 500) {
            return cleaned.substring(0, 497) + "...";
        }
        return cleaned;
    }
}
