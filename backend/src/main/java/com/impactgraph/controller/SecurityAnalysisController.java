package com.impactgraph.controller;

import com.impactgraph.dto.*;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;
import com.impactgraph.repository.SecurityFindingRepository;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.security.analysis.AiExplanationService;
import com.impactgraph.security.analysis.ApiInventoryService;
import com.impactgraph.security.analysis.SecurityAnalysisService;
import com.impactgraph.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing security-aware change impact findings, API inventory,
 * verification workflows, and AI-assisted explanations.
 * Enforces project membership and role checks for multi-tenant isolation.
 */
@RestController
@RequestMapping("/api/security")
public class SecurityAnalysisController {

    private final SecurityAnalysisService analysisService;
    private final ApiInventoryService apiInventoryService;
    private final AiExplanationService aiExplanationService;
    private final SecurityFindingRepository findingRepository;
    private final ProjectService projectService;

    public SecurityAnalysisController(
            SecurityAnalysisService analysisService,
            ApiInventoryService apiInventoryService,
            AiExplanationService aiExplanationService,
            SecurityFindingRepository findingRepository,
            ProjectService projectService
    ) {
        this.analysisService = analysisService;
        this.apiInventoryService = apiInventoryService;
        this.aiExplanationService = aiExplanationService;
        this.findingRepository = findingRepository;
        this.projectService = projectService;
    }

    /**
     * Retrieves filtered security findings.
     */
    @GetMapping("/findings")
    public ResponseEntity<List<SecurityFindingDto>> getFindings(
            @RequestParam(required = false) FindingStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (projectId != null) {
            projectService.enforceProjectAccess(user, projectId);
        }
        return ResponseEntity.ok(analysisService.getFindings(status, severity, ruleId, projectId));
    }

    /**
     * Retrieves a single security finding by ID.
     */
    @GetMapping("/findings/{id}")
    public ResponseEntity<SecurityFindingDto> getFindingById(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        return findingRepository.findById(id)
                .map(f -> {
                    if (f.getProjectId() != null) {
                        projectService.enforceProjectAccess(user, f.getProjectId());
                    }
                    return ResponseEntity.ok(SecurityFindingDto.from(f));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deterministically verifies whether a finding has been resolved.
     */
    @PostMapping("/findings/{id}/verify")
    public ResponseEntity<VerificationResultDto> verifyFinding(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        SecurityFinding finding = findingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + id));
        if (finding.getProjectId() != null) {
            projectService.enforceProjectAccess(user, finding.getProjectId());
        }
        return ResponseEntity.ok(analysisService.verifyFinding(id));
    }

    /**
     * Retrieves the security-annotated API inventory.
     */
    @GetMapping("/api-inventory")
    public ResponseEntity<List<ApiInventoryItemDto>> getApiInventory() {
        return ResponseEntity.ok(apiInventoryService.buildCurrentApiInventory());
    }

    /**
     * Retrieves previous analysis runs.
     */
    @GetMapping("/history")
    public ResponseEntity<List<AnalysisRunDto>> getAnalysisHistory(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (projectId != null) {
            projectService.enforceProjectAccess(user, projectId);
        }
        return ResponseEntity.ok(analysisService.getAnalysisHistory(projectId));
    }

    /**
     * Executes security change-impact analysis against a target revision.
     */
    @PostMapping("/analyze")
    public ResponseEntity<SecurityAnalysisResponseDto> analyze(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String baseRevision,
            @RequestParam(required = false) String targetRevision,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        if (projectId != null) {
            projectService.enforceProjectAccess(user, projectId);
        }
        return ResponseEntity.ok(analysisService.runAnalysis(projectId, baseRevision, targetRevision));
    }

    /**
     * Generates structured explanation and remediation advice for a finding.
     */
    @GetMapping("/findings/{id}/ai-explanation")
    public ResponseEntity<AiExplanationDto> getAiExplanation(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtAuthenticatedUser user
    ) {
        SecurityFinding finding = findingRepository.findById(id).orElse(null);
        if (finding == null) {
            return ResponseEntity.notFound().build();
        }
        if (finding.getProjectId() != null) {
            projectService.enforceProjectAccess(user, finding.getProjectId());
        }
        return ResponseEntity.ok(aiExplanationService.explainFinding(finding));
    }
}
