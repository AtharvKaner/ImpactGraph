package com.impactgraph.controller;

import com.impactgraph.dto.request.DependencyRequest;
import com.impactgraph.dto.response.DependencyResponse;
import com.impactgraph.service.DependencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dependencies")
public class DependencyController {

    private final DependencyService dependencyService;

    public DependencyController(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    @PostMapping
    public ResponseEntity<DependencyResponse> createDependency(@Valid @RequestBody DependencyRequest request) {
        DependencyResponse created = dependencyService.createDependency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<DependencyResponse>> getAllDependencies(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long sourceServiceId,
            @RequestParam(required = false) Long targetServiceId) {
        return ResponseEntity.ok(dependencyService.getAllDependencies(projectId, sourceServiceId, targetServiceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DependencyResponse> getDependencyById(@PathVariable Long id) {
        return ResponseEntity.ok(dependencyService.getDependencyById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DependencyResponse> updateDependency(@PathVariable Long id,
                                                               @Valid @RequestBody DependencyRequest request) {
        return ResponseEntity.ok(dependencyService.updateDependency(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDependency(@PathVariable Long id) {
        dependencyService.deleteDependency(id);
        return ResponseEntity.noContent().build();
    }
}
