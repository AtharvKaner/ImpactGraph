package com.impactgraph.graph;

/**
 * Strategy interface for detecting directed cycles within a {@link DependencyGraph}.
 */
public interface CycleDetector {

    /**
     * Inspects the given graph across all connected and disconnected components to detect directed cycles.
     *
     * @param graph the dependency graph to inspect
     * @return {@link CycleDetectionResult} indicating presence of cycles and details of detected cycles
     */
    CycleDetectionResult detectCycles(DependencyGraph graph);
}
