package com.impactgraph.controller;

import com.impactgraph.dto.repoanalysis.*;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.repoanalysis.GitOperationsService;
import com.impactgraph.service.repoanalysis.RepositoryAnalysisService;
import com.impactgraph.service.repoanalysis.RevisionComparisonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * REST controller providing repository import, architectural analysis,
 * and Git revision comparison endpoints.
 */
@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryAnalysisService analysisService;
    private final RevisionComparisonService revisionComparisonService;
    private final GitOperationsService gitOperationsService;

    @Autowired
    public RepositoryController(RepositoryAnalysisService analysisService,
                                RevisionComparisonService revisionComparisonService,
                                GitOperationsService gitOperationsService) {
        this.analysisService = analysisService;
        this.revisionComparisonService = revisionComparisonService;
        this.gitOperationsService = gitOperationsService;
    }

    /**
     * Imports and analyzes a public/private GitHub repository or local directory.
     */
    @PostMapping("/import")
    public ResponseEntity<RepositoryAnalysisResponseDto> importRepository(
            @Valid @RequestBody RepositoryImportRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        RepositoryAnalysisResponseDto response = analysisService.importAndAnalyze(request, user, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Compares two Git revisions and computes the change-impact blast radius.
     */
    @PostMapping("/compare-revisions")
    public ResponseEntity<RevisionComparisonResponseDto> compareRevisions(
            @Valid @RequestBody RevisionComparisonRequest request,
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        RevisionComparisonResponseDto response = revisionComparisonService.compareRevisions(request, user, ipAddress);
        return ResponseEntity.ok(response);
    }

    /**
     * Discovers and returns information about the current local workspace repository.
     */
    @GetMapping("/local-workspace")
    public ResponseEntity<Map<String, Object>> getLocalWorkspaceInfo() {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        boolean hasGit = Files.exists(userDir.resolve(".git"));
        String branch = hasGit ? gitOperationsService.getCurrentBranch(userDir) : "unknown";
        String sha = hasGit ? gitOperationsService.getHeadCommitSha(userDir) : "unknown";

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("path", userDir.toString());
        info.put("name", userDir.getFileName() != null ? userDir.getFileName().toString() : "workspace");
        info.put("hasGit", hasGit);
        info.put("branch", branch);
        info.put("commitSha", sha);
        info.put("isAuthorized", true);
        return ResponseEntity.ok(info);
    }

    /**
     * Retrieves recent commit revisions for the workspace or project.
     */
    @GetMapping("/revisions")
    public ResponseEntity<List<Map<String, String>>> getWorkspaceRevisions() {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (Files.exists(userDir.resolve(".git"))) {
            return ResponseEntity.ok(gitOperationsService.getRecentCommitLogs(userDir, 10));
        }
        return ResponseEntity.ok(List.of());
    }
}
