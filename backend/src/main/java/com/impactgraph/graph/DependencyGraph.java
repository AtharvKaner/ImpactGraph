package com.impactgraph.graph;

import java.util.Collection;
import java.util.Set;

/**
 * Contract for a directed dependency graph representing services and their dependencies.
 * <p>
 * <b>Direction Convention:</b> An edge {@code A -> B} means service {@code A} depends on service {@code B}.
 * <ul>
 *   <li>Forward lookup ({@link #getDependencies(String)}): What does service {@code A} depend on?</li>
 *   <li>Reverse lookup ({@link #getDependents(String)}): Which services depend on service {@code B}?</li>
 * </ul>
 */
public interface DependencyGraph {

    /**
     * Registers a service ID in the graph.
     *
     * @param serviceId unique, non-blank service identifier
     */
    void addService(String serviceId);

    /**
     * Adds a directed dependency where {@code dependentId} depends on {@code dependencyId} ({@code dependentId -> dependencyId}).
     * Both services must already be registered in the graph; missing services are NOT created silently.
     *
     * @param dependentId  the service declaring the dependency (source node)
     * @param dependencyId the service being depended upon (target node)
     */
    void addDependency(String dependentId, String dependencyId);

    /**
     * Adds a validated {@link DependencyEdge}. Both services must already be registered in the graph.
     *
     * @param edge the directed dependency edge
     */
    default void addDependency(DependencyEdge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("Dependency edge cannot be null");
        }
        addDependency(edge.dependentId(), edge.dependencyId());
    }

    /**
     * Returns an unmodifiable set of all registered service IDs in the graph.
     */
    Set<String> getServices();

    /**
     * Returns an unmodifiable set of all directed dependency edges in the graph.
     */
    Set<DependencyEdge> getEdges();

    /**
     * Returns an unmodifiable set of direct dependencies for the given service.
     * If {@code A -> B}, {@code getDependencies("A")} contains {@code "B"}.
     *
     * @param serviceId the service ID to inspect
     * @return set of service IDs that this service directly depends on
     */
    Set<String> getDependencies(String serviceId);

    /**
     * Returns an unmodifiable set of direct dependents for the given service.
     * If {@code A -> B}, {@code getDependents("B")} contains {@code "A"}.
     *
     * @param serviceId the service ID to inspect
     * @return set of service IDs that directly depend on this service
     */
    Set<String> getDependents(String serviceId);

    /**
     * Checks if a service ID is registered in the graph.
     */
    boolean hasService(String serviceId);

    /**
     * Checks if a directed dependency edge exists from {@code dependentId} to {@code dependencyId}.
     */
    boolean hasEdge(String dependentId, String dependencyId);

    /**
     * Total number of registered services (nodes) in the graph.
     */
    int getServiceCount();

    /**
     * Total number of unique dependency edges in the graph.
     */
    int getEdgeCount();

    /**
     * Checks if the graph has no registered services.
     */
    boolean isEmpty();

    /**
     * Creates a new empty mutable {@link DependencyGraph}.
     */
    static DependencyGraph create() {
        return new DefaultDependencyGraph();
    }

    /**
     * Creates an empty {@link DependencyGraph}.
     */
    static DependencyGraph empty() {
        return new DefaultDependencyGraph();
    }

    /**
     * Creates a {@link DependencyGraph} populated with the given services and dependencies.
     *
     * @param services     collection of service IDs to register
     * @param dependencies collection of dependency edges to establish
     * @return populated {@link DependencyGraph}
     */
    static DependencyGraph of(Collection<String> services, Collection<DependencyEdge> dependencies) {
        return builder().addServices(services).addDependencies(dependencies).build();
    }

    /**
     * Obtains a new {@link Builder} to construct a {@link DependencyGraph}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing a {@link DependencyGraph}.
     */
    class Builder {
        private final DependencyGraph graph = new DefaultDependencyGraph();

        public Builder addService(String serviceId) {
            graph.addService(serviceId);
            return this;
        }

        public Builder addServices(Collection<String> serviceIds) {
            if (serviceIds == null) {
                throw new IllegalArgumentException("Services collection cannot be null");
            }
            for (String s : serviceIds) {
                graph.addService(s);
            }
            return this;
        }

        public Builder addDependency(String dependentId, String dependencyId) {
            graph.addDependency(dependentId, dependencyId);
            return this;
        }

        public Builder addDependency(DependencyEdge edge) {
            graph.addDependency(edge);
            return this;
        }

        public Builder addDependencies(Collection<DependencyEdge> edges) {
            if (edges == null) {
                throw new IllegalArgumentException("Dependencies collection cannot be null");
            }
            for (DependencyEdge edge : edges) {
                graph.addDependency(edge);
            }
            return this;
        }

        public DependencyGraph build() {
            return graph;
        }
    }
}
