package com.impactgraph.graph;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a detected directed cycle within a {@link DependencyGraph}.
 * <p>
 * A cycle is represented as a sequence of service IDs where the first and last elements are identical,
 * e.g., {@code ["A", "B", "C", "A"]}, indicating that {@code A -> B -> C -> A}.
 */
public record DirectedCycle(List<String> nodes) {

    public DirectedCycle {
        Objects.requireNonNull(nodes, "Cycle nodes list cannot be null");
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("A cycle must contain at least 2 nodes (e.g. [A, A] for self-loop)");
        }
        if (!nodes.getFirst().equals(nodes.getLast())) {
            throw new IllegalArgumentException("First and last nodes of a cycle must be identical");
        }
        nodes = List.copyOf(nodes);
    }

    /**
     * Number of dependency edges in the cycle.
     */
    public int length() {
        return nodes.size() - 1;
    }

    /**
     * Formats the cycle as a human-readable arrow sequence, e.g. {@code "A -> B -> C -> A"}.
     */
    public String toCycleString() {
        return String.join(" -> ", nodes);
    }
}
