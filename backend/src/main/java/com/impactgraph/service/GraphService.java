package com.impactgraph.service;

import com.impactgraph.dto.*;
import com.impactgraph.graph.*;
import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Service orchestrating graph queries, dependency visualization, change-impact analysis,
 * and cycle detection.
 * <p>
 * Connects web controllers with the domain graph engine ({@link DependencyGraph},
 * {@link ImpactAnalyzer}, and {@link CycleDetector}).
 */
@Service
public class GraphService {

    private final DemonstrationGraphCatalog catalog;
    private final ImpactAnalyzer impactAnalyzer;
    private final CycleDetector cycleDetector;
    private final DependencyService dependencyService;

    @Autowired
    public GraphService(
            DemonstrationGraphCatalog catalog,
            ImpactAnalyzer impactAnalyzer,
            CycleDetector cycleDetector,
            DependencyService dependencyService
    ) {
        this.catalog = Objects.requireNonNull(catalog, "DemonstrationGraphCatalog cannot be null");
        this.impactAnalyzer = Objects.requireNonNull(impactAnalyzer, "ImpactAnalyzer cannot be null");
        this.cycleDetector = Objects.requireNonNull(cycleDetector, "CycleDetector cannot be null");
        this.dependencyService = dependencyService;
    }

    public GraphService(
            DemonstrationGraphCatalog catalog,
            ImpactAnalyzer impactAnalyzer,
            CycleDetector cycleDetector
    ) {
        this(catalog, impactAnalyzer, cycleDetector, null);
    }

    /**
     * Retrieves the service directory for a given topology preset.
     *
     * @param preset the preset identifier (defaults to "ecommerce-dag" if null or empty)
     * @return list of services with metadata
     */
    public List<ServiceDto> getServices(String preset) {
        String normalized = catalog.normalizePreset(preset);
        return catalog.getServices(normalized);
    }

    /**
     * Builds and returns the graph topology response including services, directed edges,
     * cycle status, and detected cycles.
     *
     * @param preset the preset identifier (defaults to "ecommerce-dag" if null or empty)
     * @return structured graph response DTO
     */
    public GraphResponseDto getGraph(String preset) {
        String normalized = catalog.normalizePreset(preset);
        DependencyGraph graph = catalog.getGraph(normalized);
        CycleDetectionResult cycleResult = cycleDetector.detectCycles(graph);

        List<ServiceDto> services = catalog.getServices(normalized);
        List<DependencyEdgeDto> dependencies = graph.getEdges().stream()
                .map(DependencyEdgeDto::from)
                .toList();
        List<CycleDto> cycles = cycleResult.cycles().stream()
                .map(CycleDto::from)
                .toList();

        return new GraphResponseDto(
                normalized,
                catalog.getPresetName(normalized),
                catalog.getPresetDescription(normalized),
                graph.getServiceCount(),
                graph.getEdgeCount(),
                cycleResult.hasCycle(),
                services,
                dependencies,
                cycles
        );
    }

    /**
     * Analyzes downstream change impact starting from {@code changedServiceId} using demo catalog preset.
     *
     * @param preset           the preset identifier (defaults to "ecommerce-dag" if null or empty)
     * @param changedServiceId the ID of the service undergoing change
     * @return impact analysis response DTO with blast radius and dependency paths
     */
    public ImpactAnalysisResponseDto analyzeImpact(String preset, String changedServiceId) {
        return analyzeImpact(preset, changedServiceId, null);
    }

    /**
     * Analyzes downstream change impact starting from {@code changedServiceId}.
     * When {@code projectId} is provided, loads the project graph from PostgreSQL via
     * {@link DependencyService#buildDependencyGraphForProject(Long)}.
     * Otherwise, uses the specified demonstration catalog preset.
     *
     * @param preset           the preset identifier (defaults to "ecommerce-dag" if null or empty)
     * @param changedServiceId the ID or name of the service undergoing change
     * @param projectId        optional project ID for persisted graph analysis
     * @return impact analysis response DTO with blast radius and dependency paths
     */
    public ImpactAnalysisResponseDto analyzeImpact(String preset, String changedServiceId, Long projectId) {
        if (changedServiceId == null || changedServiceId.isBlank()) {
            throw new InvalidServiceIdException("Changed service ID cannot be null or blank");
        }
        String decoded;
        try {
            decoded = java.net.URLDecoder.decode(changedServiceId, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidServiceIdException("Invalid URL-encoded service ID: " + changedServiceId);
        }
        if (decoded.isBlank()) {
            throw new InvalidServiceIdException("Changed service ID cannot be null or blank");
        }
        String serviceId = decoded.trim();

        DependencyGraph graph;
        String targetService;

        if (projectId != null) {
            if (dependencyService == null) {
                throw new IllegalStateException("DependencyService is not configured for persisted project graph analysis");
            }
            graph = dependencyService.buildDependencyGraphForProject(projectId);
            targetService = dependencyService.resolveServiceName(projectId, serviceId);
        } else {
            String normalized = catalog.normalizePreset(preset);
            graph = catalog.getGraph(normalized);
            targetService = serviceId;
        }

        if (!graph.hasService(targetService)) {
            throw new UnknownServiceException(
                    "Changed service '" + serviceId + "' is not registered in the graph"
            );
        }

        ImpactAnalysisResult analysis = impactAnalyzer.analyzeImpact(graph, targetService);
        CycleDetectionResult cycleResult = cycleDetector.detectCycles(graph);

        List<ImpactPathDto> sortedPaths = analysis.getSortedImpactPaths().stream()
                .map(ImpactPathDto::from)
                .toList();

        return new ImpactAnalysisResponseDto(
                analysis.changedServiceId(),
                analysis.getAffectedCount(),
                analysis.affectedServiceIds(),
                analysis.getDirectAffectedServices(),
                analysis.getTransitiveAffectedServices(),
                sortedPaths,
                CycleDetectionResponseDto.from(cycleResult)
        );
    }

    /**
     * Performs cycle detection on the specified preset topology.
     *
     * @param preset the preset identifier (defaults to "ecommerce-dag" if null or empty)
     * @return cycle detection results
     */
    public CycleDetectionResponseDto detectCycles(String preset) {
        String normalized = catalog.normalizePreset(preset);
        DependencyGraph graph = catalog.getGraph(normalized);
        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        return CycleDetectionResponseDto.from(result);
    }
}
