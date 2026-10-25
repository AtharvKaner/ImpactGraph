package com.impactgraph.controller;

import com.impactgraph.dto.MembershipRequest;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints for user and project management.
 * All endpoints require ADMIN role (enforced by SecurityConfig URL pattern + @PreAuthorize).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProjectService projectService;

    public AdminController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Grant project membership to a user.
     */
    @PostMapping("/memberships")
    public ResponseEntity<Map<String, String>> grantMembership(
            @Valid @RequestBody MembershipRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser admin,
            HttpServletRequest httpRequest) {
        projectService.grantMembership(
                request.getUserEmail(), request.getProjectId(),
                admin, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Membership granted"));
    }

    /**
     * Revoke project membership from a user.
     */
    @DeleteMapping("/memberships")
    public ResponseEntity<Map<String, String>> revokeMembership(
            @Valid @RequestBody MembershipRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser admin,
            HttpServletRequest httpRequest) {
        projectService.revokeMembership(
                request.getUserEmail(), request.getProjectId(),
                admin, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Membership revoked"));
    }
}
