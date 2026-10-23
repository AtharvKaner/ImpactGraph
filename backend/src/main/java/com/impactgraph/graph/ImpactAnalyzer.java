package com.impactgraph.graph;

/**
 * Strategy interface for performing change-impact analysis on a {@link DependencyGraph}.
 */
public interface ImpactAnalyzer {

    /**
     * Identifies all direct and transitive dependents affected by a change to {@code changedServiceId}.
     *
     * @param graph            the dependency graph to analyze
     * @param changedServiceId the identifier of the changed service
     * @return {@link ImpactAnalysisResult} containing affected services and explanatory dependency paths
     */
    ImpactAnalysisResult analyzeImpact(DependencyGraph graph, String changedServiceId);
}
