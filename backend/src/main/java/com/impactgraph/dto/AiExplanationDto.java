package com.impactgraph.dto;

import java.util.List;
import java.util.UUID;

public record AiExplanationDto(
        UUID findingId,
        String ruleId,
        String threatSummary,
        String impactRationale,
        String suggestedRemediation,
        String remediationCodeSnippet,
        List<String> suggestedRegressionTests,
        String limitationsAndUncertainty,
        boolean isAiGenerated,
        String modelProvider
) {}
