package com.impactgraph.dto;

import com.impactgraph.entity.Confidence;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;

import java.time.Instant;
import java.util.UUID;

public record SecurityFindingDto(
        UUID id,
        String ruleId,
        String title,
        String description,
        Severity severity,
        Confidence confidence,
        FindingStatus status,
        String baseRevision,
        String targetRevision,
        String filePath,
        Integer startLine,
        Integer endLine,
        String affectedEndpoint,
        String httpMethod,
        String affectedComponent,
        String evidencePath,
        String explanation,
        String suggestedRemediation,
        String suggestedTests,
        String verificationNotes,
        Long projectId,
        Instant createdAt,
        Instant verifiedAt
) {
    public static SecurityFindingDto from(SecurityFinding f) {
        if (f == null) return null;
        return new SecurityFindingDto(
                f.getId(),
                f.getRuleId(),
                f.getTitle(),
                f.getDescription(),
                f.getSeverity(),
                f.getConfidence(),
                f.getStatus(),
                f.getBaseRevision(),
                f.getTargetRevision(),
                f.getFilePath(),
                f.getStartLine(),
                f.getEndLine(),
                f.getAffectedEndpoint(),
                f.getHttpMethod(),
                f.getAffectedComponent(),
                f.getEvidencePath(),
                f.getExplanation(),
                f.getSuggestedRemediation(),
                f.getSuggestedTests(),
                f.getVerificationNotes(),
                f.getProjectId(),
                f.getCreatedAt(),
                f.getVerifiedAt()
        );
    }
}
