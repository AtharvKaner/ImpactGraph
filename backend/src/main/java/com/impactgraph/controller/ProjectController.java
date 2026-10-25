package com.impactgraph.controller;

import com.impactgraph.dto.*;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Project management endpoints.
 * All endpoints require authentication. Project-level authorization is enforced
 * by the service layer (ADMIN=all, DEVELOPER=membership only).
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * List projects accessible to the current user.
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listProjects(
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.ok(projectService.listProjects(user));
    }

    /**
     * Get a single project (with access check).
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.ok(projectService.getProject(projectId, user));
    }

    /**
     * Create a project. Admin only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(projectService.createProject(request, user, httpRequest.getRemoteAddr()));
    }

    /**
     * Update a project. Admin only.
     */
    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request, user, httpRequest.getRemoteAddr()));
    }

    /**
     * Delete a project. Admin only.
     */
    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        projectService.deleteProject(projectId, user, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * List members of a project. Requires project access.
     */
    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectService.MemberInfo>> listMembers(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.ok(projectService.listMembers(projectId, user));
    }
}
