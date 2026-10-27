package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;

import java.util.Objects;

/**
 * Represents a directed dependency relationship between two services.
 * <p>
 * <b>Direction Convention:</b> {@code A -> B} means service {@code A} depends on service {@code B}.
 * <ul>
 *   <li>{@link #dependentId()}: The service that declares the dependency (source node {@code A}).</li>
 *   <li>{@link #dependencyId()}: The target service that is depended upon (target node {@code B}).</li>
 * </ul>
 */
public record DependencyEdge(String dependentId, String dependencyId) {

    public DependencyEdge {
        if (dependentId == null || dependentId.isBlank()) {
            throw new InvalidServiceIdException("Dependent service ID cannot be null or blank");
        }
        if (dependencyId == null || dependencyId.isBlank()) {
            throw new InvalidServiceIdException("Dependency service ID cannot be null or blank");
        }
        dependentId = dependentId.trim();
        dependencyId = dependencyId.trim();
    }

    /**
     * Factory method to create a dependency edge where {@code dependentId} depends on {@code dependencyId}.
     *
     * @param dependentId  the service that depends on dependencyId (source)
     * @param dependencyId the service that dependentId depends on (target)
     * @return a new validated {@link DependencyEdge}
     */
    public static DependencyEdge of(String dependentId, String dependencyId) {
        return new DependencyEdge(dependentId, dependencyId);
    }

    /**
     * Formats the edge following the {@code dependent -> dependency} convention.
     *
     * @return representation such as {@code "OrderService -> PaymentService"}
     */
    public String toArrowString() {
        return dependentId + " -> " + dependencyId;
    }
}
