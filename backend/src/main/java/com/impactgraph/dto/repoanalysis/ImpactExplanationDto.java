package com.impactgraph.dto.repoanalysis;

import java.util.List;

/**
 * Explains how and why a source component or API endpoint is affected by a revision change,
 * with supporting source code evidence, dependency path, and recommended verification steps.
 *
 * @param targetId                   component name or endpoint string (e.g. "Order Service" or "POST /api/orders")
 * @param targetType                 "COMPONENT" or "ENDPOINT"
 * @param changeOrigin               root modified service or file
 * @param impactType                 "DIRECT" or "TRANSITIVE"
 * @param dependencyPath             ordered sequence of components from target to origin
 * @param pathString                 arrow-formatted path string
 * @param sourceEvidence             file path, line number, or code snippet establishing the relationship
 * @param explanation                human-readable rationale for the potential impact
 * @param confidence                 "HIGH" or "MEDIUM"
 * @param recommendedVerificationSteps actionable verification guidelines
 * @param relevantTests              candidate test classes or test methods to execute
 * @param isPotentialImpactOnly      true to distinguish potential dependency-reachability impact from confirmed regression
 */
public record ImpactExplanationDto(
        String targetId,
        String targetType,
        String changeOrigin,
        String impactType,
        List<String> dependencyPath,
        String pathString,
        String sourceEvidence,
        String explanation,
        String confidence,
        List<String> recommendedVerificationSteps,
        List<String> relevantTests,
        boolean isPotentialImpactOnly
) {
}
