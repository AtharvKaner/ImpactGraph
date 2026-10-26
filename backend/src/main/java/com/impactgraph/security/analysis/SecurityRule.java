package com.impactgraph.security.analysis;

import com.impactgraph.dto.ApiInventoryItemDto;
import com.impactgraph.entity.Confidence;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interface representing a deterministic security rule for change-impact analysis.
 */
public interface SecurityRule {

    /**
     * Unique stable rule identifier.
     */
    String getRuleId();

    /**
     * Human-readable rule title.
     */
    String getTitle();

    /**
     * Severity rationale.
     */
    Severity getDefaultSeverity();

    /**
     * Evaluates endpoints, source analysis, and code changes to produce security findings.
     */
    List<SecurityFinding> evaluate(
            List<ApiInventoryItemDto> currentApis,
            JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
            String baseRevision,
            String targetRevision
    );
}
