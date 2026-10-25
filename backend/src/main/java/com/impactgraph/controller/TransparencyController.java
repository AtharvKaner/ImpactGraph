package com.impactgraph.controller;

import com.impactgraph.entity.AuditEvent;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data transparency endpoints.
 * <p>
 * Provides information about what data ImpactGraph collects, how it's stored,
 * and what roles can access it. Also shows the user their own recent audit events.
 * </p>
 * <p>
 * Claims in this endpoint reflect actual application behavior, not aspirational features.
 * </p>
 */
@RestController
@RequestMapping("/api/transparency")
public class TransparencyController {

    private final AuditService auditService;

    public TransparencyController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Public transparency information — no authentication required.
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> getPublicTransparency() {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("dataCollected", List.of(
                Map.of("category", "Account Identity",
                       "fields", List.of("Email address", "Display name", "Hashed password (BCrypt)", "Role (ADMIN or DEVELOPER)"),
                       "purpose", "Authentication and authorization"),
                Map.of("category", "Project Metadata",
                       "fields", List.of("Project name", "Description", "Creation/update timestamps"),
                       "purpose", "Organizing dependency analysis workspaces"),
                Map.of("category", "Project Membership",
                       "fields", List.of("User-to-project associations", "Who granted membership"),
                       "purpose", "Controlling project-level access"),
                Map.of("category", "Security Events (Audit Log)",
                       "fields", List.of("Event type", "Timestamp", "Actor email", "Project/resource ID", "Outcome", "Client IP"),
                       "purpose", "Security monitoring and compliance"),
                Map.of("category", "Services, APIs, and Dependencies",
                       "fields", List.of("Defined by core module (under development)"),
                       "purpose", "Software dependency and change-impact analysis")
        ));

        info.put("dataStorage", Map.of(
                "database", "PostgreSQL",
                "location", "Configured by deployment (see application.properties DB_URL)",
                "encryption", "Not enforced by the application layer. Depends on PostgreSQL and infrastructure configuration.",
                "backups", "Not managed by the application. Depends on infrastructure setup."
        ));

        info.put("graphAnalysis", Map.of(
                "description", "ImpactGraph analyzes software dependencies to determine change impact across services and APIs.",
                "dataUsed", "Project services, APIs, and dependency relationships",
                "status", "Core graph analysis module is under development by the backend team."
        ));

        info.put("accessControl", Map.of(
                "ADMIN", "Can manage all projects, grant/revoke memberships, view audit logs, and administer users.",
                "DEVELOPER", "Can access only projects they have been granted membership to. Cannot view audit logs or manage other users."
        ));

        info.put("dataRetention", Map.of(
                "auditEvents", "Retained indefinitely in the database. No automatic deletion is currently implemented.",
                "userAccounts", "Persist until manually removed by an administrator.",
                "limitation", "Automatic data retention policies and scheduled deletion are not yet implemented."
        ));

        return ResponseEntity.ok(info);
    }

    /**
     * Authenticated endpoint — returns the user's own recent audit events.
     */
    @GetMapping("/my-activity")
    public ResponseEntity<Map<String, Object>> getMyActivity(
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        Page<AuditEvent> events = auditService.getEventsByActor(
                user.getUserId(), PageRequest.of(page, size));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        result.put("recentActivity", events.getContent());
        result.put("totalEvents", events.getTotalElements());
        result.put("page", events.getNumber());
        result.put("totalPages", events.getTotalPages());

        return ResponseEntity.ok(result);
    }
}
