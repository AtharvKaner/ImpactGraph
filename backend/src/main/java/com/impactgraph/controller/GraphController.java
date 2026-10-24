package com.impactgraph.controller;

import com.impactgraph.dto.CycleDetectionResponseDto;
import com.impactgraph.dto.GraphResponseDto;
import com.impactgraph.dto.ImpactAnalysisResponseDto;
import com.impactgraph.dto.ServiceDto;
import com.impactgraph.service.GraphService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * REST controller exposing service catalog metadata, graph topologies,
 * change-impact blast-radius analysis, and cycle detection endpoints.
 */
@RestController
@RequestMapping("/api")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = Objects.requireNonNull(graphService, "GraphService cannot be null");
    }

    /**
     * Retrieves the list of services for the specified preset.
     *
     * @param preset topology preset (defaults to "ecommerce-dag")
     * @return 200 OK with list of services
     */
    @GetMapping("/services")
    public ResponseEntity<List<ServiceDto>> getServices(
            @RequestParam(name = "preset", required = false) String preset
    ) {
        List<ServiceDto> services = graphService.getServices(preset);
        return ResponseEntity.ok(services);
    }

    /**
     * Retrieves the graph topology, services, directed edges, and cycle status.
     *
     * @param preset topology preset (defaults to "ecommerce-dag")
     * @return 200 OK with graph response
     */
    @GetMapping("/graph")
    public ResponseEntity<GraphResponseDto> getGraph(
            @RequestParam(name = "preset", required = false) String preset
    ) {
        GraphResponseDto graph = graphService.getGraph(preset);
        return ResponseEntity.ok(graph);
    }

    /**
     * Calculates the change impact blast radius for a given service.
     * When projectId is provided, analyzes the persisted graph from PostgreSQL;
     * otherwise analyzes the demonstration catalog preset.
     *
     * @param serviceId the changed service identifier
     * @param preset    topology preset (defaults to "ecommerce-dag")
     * @param projectId optional project ID for persisted graph analysis
     * @return 200 OK with impact analysis results
     */
    @GetMapping({"/impact", "/impact/{serviceId}"})
    public ResponseEntity<ImpactAnalysisResponseDto> analyzeImpact(
            @PathVariable(name = "serviceId", required = false) String serviceId,
            @RequestParam(name = "preset", required = false) String preset,
            @RequestParam(name = "projectId", required = false) Long projectId
    ) {
        ImpactAnalysisResponseDto impact = graphService.analyzeImpact(preset, serviceId, projectId);
        return ResponseEntity.ok(impact);
    }

    /**
     * Detects directed dependency cycles for the specified preset.
     *
     * @param preset topology preset (defaults to "ecommerce-dag")
     * @return 200 OK with cycle detection response
     */
    @GetMapping("/cycles")
    public ResponseEntity<CycleDetectionResponseDto> getCycles(
            @RequestParam(name = "preset", required = false) String preset
    ) {
        CycleDetectionResponseDto cycles = graphService.detectCycles(preset);
        return ResponseEntity.ok(cycles);
    }
}
