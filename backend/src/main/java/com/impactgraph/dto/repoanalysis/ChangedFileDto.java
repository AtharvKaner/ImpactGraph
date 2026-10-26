package com.impactgraph.dto.repoanalysis;

/**
 * DTO representing a changed file between two Git revisions.
 */
public record ChangedFileDto(
        String filePath,
        String changeType, // ADDED, MODIFIED, DELETED, RENAMED
        String oldPath,
        String mappedServiceName
) {
}
