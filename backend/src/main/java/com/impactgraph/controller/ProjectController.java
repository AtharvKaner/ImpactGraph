package com.impactgraph.controller;

import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.response.DependencyResponse;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.DependencyService;
import com.impactgraph.service.ProjectService;
import com.impactgraph.service.ServiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ServiceService serviceService;
    private final DependencyService dependencyService;

    public ProjectController(ProjectService projectService,
                             ServiceService serviceService,
                             DependencyService dependencyService) {
        this.projectService = projectService;
        this.serviceService = serviceService;
        this.dependencyService = dependencyService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        if (user != null && !"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("Access denied");
        }
        ProjectResponse created = projectService.createProject(
                request, user, httpRequest != null ? httpRequest.getRemoteAddr() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects(
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.ok(projectService.getAllProjects(user));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProjectById(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.ok(projectService.getProjectById(projectId, user));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        if (user != null && !"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("Access denied");
        }
        return ResponseEntity.ok(projectService.updateProject(
                projectId, request, user, httpRequest != null ? httpRequest.getRemoteAddr() : null));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        if (user != null && !"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("Access denied");
        }
        projectService.deleteProject(
                projectId, user, httpRequest != null ? httpRequest.getRemoteAddr() : null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectService.MemberInfo>> listMembers(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return ResponseEntity.ok(projectService.listMembers(projectId, user));
    }

    @GetMapping("/{projectId}/services")
    public ResponseEntity<List<ServiceResponse>> getServicesForProject(@PathVariable("projectId") Long projectId) {
        return ResponseEntity.ok(serviceService.getAllServices(projectId));
    }

    @GetMapping("/{projectId}/dependencies")
    public ResponseEntity<List<DependencyResponse>> getDependenciesForProject(@PathVariable("projectId") Long projectId) {
        return ResponseEntity.ok(dependencyService.getAllDependencies(projectId, null, null));
    }
}
