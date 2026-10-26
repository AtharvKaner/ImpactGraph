package com.impactgraph.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SecurityAnalysisResponseDto(
        UUID runId,
        String runType,
        String baseRevision,
        String targetRevision,
        long timestamp,
        int totalFindings,
        Map<String, Long> findingsBySeverity,
        List<SecurityFindingDto> findings,
        List<ApiInventoryItemDto> apiInventory,
        List<String> affectedServices,
        List<String> affectedEndpoints,
        CycleDetectionResponseDto cycleStatus,
        List<String> recommendedTests,
        String summary
) {}
