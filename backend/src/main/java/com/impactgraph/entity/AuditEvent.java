package com.impactgraph.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent audit event record.
 * <p>
 * Security constraints:
 * - Never log passwords, tokens, or signing secrets.
 * - Client-supplied metadata in {@code details} is treated as untrusted and truncated.
 * - Ordinary developers cannot edit or delete audit records (enforced by service/controller).
 * - Only admins may view audit logs via the API.
 * </p>
 */
@Entity
@Table(name = "audit_event", indexes = {
    @Index(name = "idx_audit_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_event_actor_id", columnList = "actorId"),
    @Index(name = "idx_audit_event_type", columnList = "eventType")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditEventType eventType;

    /**
     * The user who performed the action. Null for anonymous/failed-login events.
     */
    @Column
    private UUID actorId;

    /**
     * Email of the actor at the time of the event (denormalized for audit integrity).
     */
    @Column(length = 255)
    private String actorEmail;

    /**
     * The project associated with this event, if applicable.
     */
    @Column
    private UUID projectId;

    /**
     * The specific resource ID (e.g., dependency, membership), if applicable.
     */
    @Column
    private UUID resourceId;

    /**
     * SUCCESS or FAILURE.
     */
    @Column(nullable = false, length = 10)
    private String outcome;

    /**
     * Sanitized, truncated description. Never contains credentials or tokens.
     */
    @Column(length = 500)
    private String details;

    @Column(length = 45)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    // --- Getters and Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public AuditEventType getEventType() { return eventType; }
    public void setEventType(AuditEventType eventType) { this.eventType = eventType; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getDetails() { return details; }
    public void setDetails(String details) {
        // Truncate to prevent log injection or oversized entries
        if (details != null && details.length() > 500) {
            this.details = details.substring(0, 497) + "...";
        } else {
            this.details = details;
        }
    }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
