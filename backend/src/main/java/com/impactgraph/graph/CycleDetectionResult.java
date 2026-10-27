package com.impactgraph.graph;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result model representing the findings of a cycle detection analysis.
 * <p>
 * Decoupled from persistence and web layers.
 */
public record CycleDetectionResult(
        boolean hasCycle,
        List<DirectedCycle> cycles
) {

    public CycleDetectionResult {
        cycles = cycles == null ? Collections.emptyList() : List.copyOf(cycles);
        hasCycle = hasCycle || !cycles.isEmpty();
    }

    /**
     * Factory for a clean result with no cycles.
     */
    public static CycleDetectionResult noCycles() {
        return new CycleDetectionResult(false, Collections.emptyList());
    }

    /**
     * Factory for a result containing detected cycles.
     */
    public static CycleDetectionResult ofCycles(List<DirectedCycle> cycles) {
        return new CycleDetectionResult(!cycles.isEmpty(), cycles);
    }

    /**
     * Returns the first detected cycle, if present.
     */
    public Optional<DirectedCycle> getFirstCycle() {
        return cycles.isEmpty() ? Optional.empty() : Optional.of(cycles.getFirst());
    }

    /**
     * Total count of unique cycles detected.
     */
    public int getCycleCount() {
        return cycles.size();
    }
}
