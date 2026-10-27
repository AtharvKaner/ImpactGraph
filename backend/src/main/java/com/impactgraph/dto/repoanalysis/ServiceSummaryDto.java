package com.impactgraph.dto.repoanalysis;

import java.util.List;

/**
 * Summary DTO of a discovered service or component extracted from repository analysis.
 */
public record ServiceSummaryDto(
        String name,
        String type,
        String tier,
        String sourcePath,
        int lineNumber,
        int apiCount,
        int dependencyCount,
        List<String> apis
) {
}
