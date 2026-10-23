package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Clean domain result model representing the outcome of a software change-impact analysis.
 * <p>
 * Completely decoupled from persistence, databases, and web frameworks.
 */
public record ImpactAnalysisResult(
        String changedServiceId,
        Set<String> affectedServiceIds,
        Map<String, ImpactPath> impactPaths
) {

    public ImpactAnalysisResult {
        if (changedServiceId == null || changedServiceId.isBlank()) {
            throw new InvalidServiceIdException("Changed service ID cannot be null or blank");
        }
        changedServiceId = changedServiceId.trim();
        affectedServiceIds = Collections.unmodifiableSet(new TreeSet<>(affectedServiceIds));
        impactPaths = Collections.unmodifiableMap(new LinkedHashMap<>(impactPaths));
    }

    /**
     * Checks if any dependent services are affected by the change.
     */
    public boolean hasImpact() {
        return !affectedServiceIds.isEmpty();
    }

    /**
     * Total count of affected services (direct + transitive).
     */
    public int getAffectedCount() {
        return affectedServiceIds.size();
    }

    /**
     * Checks if a specific service is in the set of affected services.
     */
    public boolean isAffected(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return false;
        }
        return affectedServiceIds.contains(serviceId.trim());
    }

    /**
     * Retrieves the {@link ImpactPath} for a given affected service.
     */
    public Optional<ImpactPath> getImpactPath(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(impactPaths.get(serviceId.trim()));
    }

    /**
     * Retrieves the raw path nodes ({@code [affectedService, ..., changedService]}) for an affected service.
     */
    public List<String> getPath(String serviceId) {
        return getImpactPath(serviceId)
                .map(ImpactPath::path)
                .orElse(Collections.emptyList());
    }

    /**
     * Returns all services that directly depend on the changed service (distance == 1).
     */
    public Set<String> getDirectAffectedServices() {
        return impactPaths.values().stream()
                .filter(ImpactPath::isDirect)
                .map(ImpactPath::affectedServiceId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Returns all services that transitively depend on the changed service (distance &gt; 1).
     */
    public Set<String> getTransitiveAffectedServices() {
        return impactPaths.values().stream()
                .filter(ImpactPath::isTransitive)
                .map(ImpactPath::affectedServiceId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Returns the impact paths sorted deterministically by distance (ascending), then by service ID (alphabetically).
     */
    public List<ImpactPath> getSortedImpactPaths() {
        return impactPaths.values().stream()
                .sorted(Comparator.comparingInt(ImpactPath::distance)
                        .thenComparing(ImpactPath::affectedServiceId))
                .toList();
    }
}
