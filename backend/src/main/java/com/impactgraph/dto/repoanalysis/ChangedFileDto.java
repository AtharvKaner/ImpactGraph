package com.impactgraph.dto.repoanalysis;

import java.util.List;

/**
 * DTO representing a changed file between two Git revisions with line numbers and diff metrics.
 */
public record ChangedFileDto(
        String filePath,
        String changeType, // ADDED, MODIFIED, DELETED, RENAMED
        String oldPath,
        String mappedServiceName,
        int linesAdded,
        int linesDeleted,
        List<String> changedLineRanges,
        String summarySnippet
) {
    public ChangedFileDto(String filePath, String changeType, String oldPath, String mappedServiceName) {
        this(filePath, changeType, oldPath, mappedServiceName, 0, 0, List.of(), null);
    }
}

