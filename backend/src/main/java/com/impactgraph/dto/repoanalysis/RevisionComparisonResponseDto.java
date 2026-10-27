package com.impactgraph.dto.repoanalysis;

import com.impactgraph.dto.ApiInventoryItemDto;
import com.impactgraph.dto.ImpactPathDto;
import com.impactgraph.dto.SecurityFindingDto;

import java.util.List;
import java.util.UUID;

/**
 * Result DTO of comparing two Git revisions, mapping changed files to components,
 * and evaluating blast radius, source evidence explanations, and security findings.
 */
public record RevisionComparisonResponseDto(
        Long projectId,
        String baseRevision,
        String targetRevision,
        List<ChangedFileDto> changedFiles,
        List<String> directlyChangedServices,
        List<String> directlyAffectedServices,
        List<String> transitivelyAffectedServices,
        int totalAffectedCount,
        List<ImpactPathDto> impactPaths,
        List<ApiInventoryItemDto> impactedApis,
        List<SecurityFindingDto> securityFindingsInBlastRadius,
        List<String> recommendedRegressionTests,
        String confidence,
        String limitations,
        UUID analysisRunId,
        List<ImpactExplanationDto> explanations
) {
    public RevisionComparisonResponseDto(
            Long projectId,
            String baseRevision,
            String targetRevision,
            List<ChangedFileDto> changedFiles,
            List<String> directlyChangedServices,
            List<String> directlyAffectedServices,
            List<String> transitivelyAffectedServices,
            int totalAffectedCount,
            List<ImpactPathDto> impactPaths,
            List<ApiInventoryItemDto> impactedApis,
            List<SecurityFindingDto> securityFindingsInBlastRadius,
            List<String> recommendedRegressionTests,
            String confidence,
            String limitations,
            UUID analysisRunId) {
        this(projectId, baseRevision, targetRevision, changedFiles, directlyChangedServices,
                directlyAffectedServices, transitivelyAffectedServices, totalAffectedCount,
                impactPaths, impactedApis, securityFindingsInBlastRadius, recommendedRegressionTests,
                confidence, limitations, analysisRunId, List.of());
    }
}

