package com.impactgraph.dto;

import com.impactgraph.graph.DirectedCycle;

import java.util.List;

/**
 * Data transfer object representing a detected directed cycle.
 *
 * @param nodes       ordered sequence of service IDs traversing the cycle (first and last are identical)
 * @param length      number of dependency hops in the cycle
 * @param cycleString formatted representation such as {@code "A -> B -> A"}
 */
public record CycleDto(
        List<String> nodes,
        int length,
        String cycleString
) {
    public static CycleDto from(DirectedCycle cycle) {
        return new CycleDto(cycle.nodes(), cycle.length(), cycle.toCycleString());
    }
}
