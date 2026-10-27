package com.impactgraph.security.analysis;

import com.impactgraph.dto.AiExplanationDto;
import com.impactgraph.entity.SecurityFinding;

public interface AiExplanationService {

    /**
     * Generates a security explanation, threat model summary, remediation code,
     * and regression test recommendations for a finding.
     */
    AiExplanationDto explainFinding(SecurityFinding finding);

    /**
     * Always false in this offline-only demo; remediation guidance is deterministic.
     */
    boolean isAiProviderConfigured();
}
