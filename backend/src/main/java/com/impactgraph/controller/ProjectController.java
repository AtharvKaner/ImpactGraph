package com.impactgraph.controller;

import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.response.DependencyResponse;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.service.DependencyService;
import com.impactgraph.service.ProjectService;
import com.impactgraph.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id,
                                                         @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/services")
    public ResponseEntity<List<ServiceResponse>> getServicesForProject(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.getAllServices(id));
    }

    @GetMapping("/{id}/dependencies")
    public ResponseEntity<List<DependencyResponse>> getDependenciesForProject(@PathVariable Long id) {
        return ResponseEntity.ok(dependencyService.getAllDependencies(id, null, null));
    }
}
