package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;

import java.util.*;

/**
 * Default implementation of {@link DependencyGraph} backed by adjacency lists.
 * <p>
 * Maintains both forward ({@code outgoingEdges}) and reverse ({@code incomingEdges}) adjacency sets
 * using sorted sets ({@link TreeSet}) to ensure fully deterministic traversal and iteration orders.
 * Duplicate edges are prevented by set semantics.
 */
public class DefaultDependencyGraph implements DependencyGraph {

    private final Set<String> services = new TreeSet<>();
    private final Map<String, Set<String>> outgoingEdges = new HashMap<>();
    private final Map<String, Set<String>> incomingEdges = new HashMap<>();
    private final Set<DependencyEdge> edges = new LinkedHashSet<>();

    @Override
    public void addService(String serviceId) {
        validateServiceId(serviceId);
        String id = serviceId.trim();
        if (services.add(id)) {
            outgoingEdges.put(id, new TreeSet<>());
            incomingEdges.put(id, new TreeSet<>());
        }
    }

    @Override
    public void addDependency(String dependentId, String dependencyId) {
        validateServiceId(dependentId);
        validateServiceId(dependencyId);

        String source = dependentId.trim();
        String target = dependencyId.trim();

        if (!services.contains(source)) {
            throw new UnknownServiceException(
                    "Cannot add dependency: dependent service '" + source + "' is not registered in the graph");
        }
        if (!services.contains(target)) {
            throw new UnknownServiceException(
                    "Cannot add dependency: target service '" + target + "' is not registered in the graph");
        }

        outgoingEdges.get(source).add(target);
        incomingEdges.get(target).add(source);
        edges.add(new DependencyEdge(source, target));
    }

    @Override
    public Set<String> getServices() {
        return Collections.unmodifiableSet(services);
    }

    @Override
    public Set<DependencyEdge> getEdges() {
        return Collections.unmodifiableSet(edges);
    }

    @Override
    public Set<String> getDependencies(String serviceId) {
        validateServiceId(serviceId);
        String id = serviceId.trim();
        ensureServiceExists(id);
        return Collections.unmodifiableSet(outgoingEdges.get(id));
    }

    @Override
    public Set<String> getDependents(String serviceId) {
        validateServiceId(serviceId);
        String id = serviceId.trim();
        ensureServiceExists(id);
        return Collections.unmodifiableSet(incomingEdges.get(id));
    }

    @Override
    public boolean hasService(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return false;
        }
        return services.contains(serviceId.trim());
    }

    @Override
    public boolean hasEdge(String dependentId, String dependencyId) {
        if (!hasService(dependentId) || !hasService(dependencyId)) {
            return false;
        }
        return outgoingEdges.get(dependentId.trim()).contains(dependencyId.trim());
    }

    @Override
    public int getServiceCount() {
        return services.size();
    }

    @Override
    public int getEdgeCount() {
        return edges.size();
    }

    @Override
    public boolean isEmpty() {
        return services.isEmpty();
    }

    private void validateServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidServiceIdException("Service ID cannot be null or blank");
        }
    }

    private void ensureServiceExists(String serviceId) {
        if (!services.contains(serviceId)) {
            throw new UnknownServiceException("Service '" + serviceId + "' is not registered in the graph");
        }
    }
}
