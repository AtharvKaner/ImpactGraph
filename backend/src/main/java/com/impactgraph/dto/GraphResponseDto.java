package com.impactgraph.dto;

import java.util.List;

/**
 * Data transfer object encapsulating the full graph topology, metadata, nodes, edges, and cycles.
 *
 * @param preset       preset identifier (e.g. "ecommerce-dag", "circular-demo")
 * @param name         human-readable preset name
 * @param description  detailed description of the preset topology
 * @param serviceCount total number of service nodes in the graph
 * @param edgeCount    total number of directed dependency edges
 * @param hasCycle     true if circular dependencies exist
 * @param services     list of registered services with rich metadata
 * @param dependencies list of directed dependency edges
 * @param cycles       list of detected directed cycles
 */
public record GraphResponseDto(
        String preset,
        String name,
        String description,
        int serviceCount,
        int edgeCount,
        boolean hasCycle,
        List<ServiceDto> services,
        List<DependencyEdgeDto> dependencies,
        List<CycleDto> cycles
) {
}
