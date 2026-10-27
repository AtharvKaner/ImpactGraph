package com.impactgraph.dto.repoanalysis;

import java.util.List;
import java.util.UUID;

/**
 * Detailed result of a repository import and architectural analysis.
 */
public record RepositoryAnalysisResponseDto(
        Long projectId,
        String projectName,
        String description,
        String sourceType,
        String repositoryUrl,
        String commitSha,
        String branch,
        int serviceCount,
        int dependencyCount,
        int apiCount,
        int securityFindingCount,
        int scannedFilesCount,
        List<String> primaryLanguages,
        List<ServiceSummaryDto> services,
        List<DependencySummaryDto> dependencies,
        UUID analysisRunId,
        String confidence,
        String limitations
) {
}
