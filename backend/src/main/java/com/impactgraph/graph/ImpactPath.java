package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the dependency path explaining how an affected service depends on the changed service.
 * <p>
 * Following the original dependency direction ({@code A -> B} means A depends on B), the path starts
 * with the affected service and terminates with the changed service:
 * <pre>
 * [affectedService, intermediate_1, ..., changedService]
 * </pre>
 * Each adjacent pair {@code (path[i], path[i+1])} represents a direct dependency where {@code path[i]}
 * depends on {@code path[i+1]}.
 */
public record ImpactPath(
        String affectedServiceId,
        String changedServiceId,
        List<String> path
) {

    public ImpactPath {
        if (affectedServiceId == null || affectedServiceId.isBlank()) {
            throw new InvalidServiceIdException("Affected service ID cannot be null or blank");
        }
        if (changedServiceId == null || changedServiceId.isBlank()) {
            throw new InvalidServiceIdException("Changed service ID cannot be null or blank");
        }
        Objects.requireNonNull(path, "Path list cannot be null");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path list cannot be empty");
        }
        if (!path.getFirst().equals(affectedServiceId.trim())) {
            throw new IllegalArgumentException(
                    "Path must begin with affected service ID '" + affectedServiceId + "'");
        }
        if (!path.getLast().equals(changedServiceId.trim())) {
            throw new IllegalArgumentException(
                    "Path must terminate with changed service ID '" + changedServiceId + "'");
        }
        path = List.copyOf(path);
        affectedServiceId = affectedServiceId.trim();
        changedServiceId = changedServiceId.trim();
    }

    /**
     * Distance (number of dependency hops) between the affected service and the changed service.
     * Direct dependents have a distance of 1; transitive dependents have a distance &gt; 1.
     */
    public int distance() {
        return path.size() - 1;
    }

    /**
     * Returns true if the affected service directly depends on the changed service (distance == 1).
     */
    public boolean isDirect() {
        return distance() == 1;
    }

    /**
     * Returns true if the impact is transitive (distance &gt; 1).
     */
    public boolean isTransitive() {
        return distance() > 1;
    }

    /**
     * Formats the path following original dependency arrows: {@code "ServiceA -> ServiceB -> ChangedService"}.
     */
    public String toPathString() {
        return String.join(" -> ", path);
    }
}
