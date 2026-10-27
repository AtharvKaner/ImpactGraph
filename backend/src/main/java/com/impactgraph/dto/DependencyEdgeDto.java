package com.impactgraph.dto;

import com.impactgraph.graph.DependencyEdge;

/**
 * Data transfer object representing a directed dependency relationship between two services.
 * <p>
 * Exposes React Flow-compatible {@code source} and {@code target} fields alongside domain-explicit
 * {@code dependentId} and {@code dependencyId} fields.
 * <p>
 * <b>Direction Convention:</b> {@code source} (dependent) &rarr; {@code target} (dependency).
 *
 * @param source       the service declaring the dependency (dependent)
 * @param target       the service being depended upon (dependency)
 * @param dependentId  the service declaring the dependency (alias for source)
 * @param dependencyId the service being depended upon (alias for target)
 */
public record DependencyEdgeDto(
        String source,
        String target,
        String dependentId,
        String dependencyId
) {
    public static DependencyEdgeDto from(DependencyEdge edge) {
        return new DependencyEdgeDto(
                edge.dependentId(),
                edge.dependencyId(),
                edge.dependentId(),
                edge.dependencyId()
        );
    }
}
