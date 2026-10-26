package com.impactgraph.dto.repoanalysis;

/**
 * Summary DTO representing an extracted directed dependency edge with source evidence.
 */
public record DependencySummaryDto(
        String sourceService,
        String targetService,
        String dependencyType,
        String evidenceFilePath,
        int lineNumber,
        String evidenceSnippet
) {
}
