package com.impactgraph.dto;

import com.impactgraph.entity.AnalysisRun;
import java.time.Instant;
import java.util.UUID;

public record AnalysisRunDto(
        UUID id,
        String runType,
        String baseRevision,
        String targetRevision,
        Instant timestamp,
        int totalFindings,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        String status,
        Long projectId,
        String pullRequestNumber,
        String commitSha,
        String summary
) {
    public static AnalysisRunDto from(AnalysisRun run) {
        if (run == null) return null;
        return new AnalysisRunDto(
                run.getId(),
                run.getRunType(),
                run.getBaseRevision(),
                run.getTargetRevision(),
                run.getTimestamp(),
                run.getTotalFindings(),
                run.getCriticalCount(),
                run.getHighCount(),
                run.getMediumCount(),
                run.getLowCount(),
                run.getStatus(),
                run.getProjectId(),
                run.getPullRequestNumber(),
                run.getCommitSha(),
                run.getSummary()
        );
    }
}
