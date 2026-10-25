package com.impactgraph.service;

import com.impactgraph.dto.ServiceDto;
import com.impactgraph.graph.DefaultDependencyGraph;
import com.impactgraph.graph.DependencyGraph;
import com.impactgraph.graph.exception.GraphValidationException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Catalog providing in-memory demonstration service metadata and graph topologies.
 * <p>
 * Supports both standard DAG and circular demonstration presets with deterministic service
 * metadata and dependency relationships matching the frontend demonstration datasets.
 * <p>
 * <b>Direction Convention:</b> {@code A -> B} means service {@code A} depends on service {@code B}.
 */
@Component
public class DemonstrationGraphCatalog {

    public static final String PRESET_ECOMMERCE_DAG = "ecommerce-dag";
    public static final String PRESET_CIRCULAR_DEMO = "circular-demo";
    public static final String DEFAULT_PRESET = PRESET_ECOMMERCE_DAG;

    private static final List<ServiceDto> STANDARD_SERVICES = List.of(
            new ServiceDto(
                    "Storefront Web",
                    "Storefront Web",
                    "Frontend / Gateway",
                    "Client Tier",
                    "globe",
                    "User-facing web application portal"
            ),
            new ServiceDto(
                    "Checkout Service",
                    "Checkout Service",
                    "Orchestrator",
                    "Application Tier",
                    "cart",
                    "Checkout and cart orchestration engine"
            ),
            new ServiceDto(
                    "Auth Service",
                    "Auth Service",
                    "Security / Identity",
                    "Infrastructure Tier",
                    "lock",
                    "Authentication and OAuth token issuance"
            ),
            new ServiceDto(
                    "Order Service",
                    "Order Service",
                    "Core Domain",
                    "Domain Tier",
                    "database",
                    "Order lifecycle management and persistence"
            ),
            new ServiceDto(
                    "Payment Service",
                    "Payment Service",
                    "Core Domain",
                    "Domain Tier",
                    "card",
                    "Payment gateway processing and settlement"
            ),
            new ServiceDto(
                    "Inventory Service",
                    "Inventory Service",
                    "Core Domain",
                    "Domain Tier",
                    "cube",
                    "Stock levels and warehouse availability"
            ),
            new ServiceDto(
                    "Notification Service",
                    "Notification Service",
                    "Messaging / Events",
                    "Integration Tier",
                    "bell",
                    "Email, SMS, and event dispatch service"
            ),
            new ServiceDto(
                    "Audit Logger",
                    "Audit Logger",
                    "Compliance / CNS",
                    "Security Tier",
                    "document",
                    "Immutable compliance and security audit trails"
            )
    );

    private static final Map<String, ServiceDto> SERVICE_MAP = new LinkedHashMap<>();

    static {
        for (ServiceDto service : STANDARD_SERVICES) {
            SERVICE_MAP.put(service.id(), service);
        }
    }

    /**
     * Validates whether a preset identifier is recognized.
     */
    public boolean isValidPreset(String preset) {
        if (preset == null || preset.isBlank()) {
            return false;
        }
        String p = preset.trim().toLowerCase();
        return PRESET_ECOMMERCE_DAG.equals(p) || PRESET_CIRCULAR_DEMO.equals(p);
    }

    /**
     * Normalizes a preset string. If null or blank, defaults to {@value #DEFAULT_PRESET}.
     *
     * @throws GraphValidationException if the preset is unknown
     */
    public String normalizePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            return DEFAULT_PRESET;
        }
        String p = preset.trim().toLowerCase();
        if (!isValidPreset(p)) {
            throw new GraphValidationException(
                    "Unknown preset: '" + preset + "'. Supported presets: ["
                            + PRESET_ECOMMERCE_DAG + ", " + PRESET_CIRCULAR_DEMO + "]"
            );
        }
        return p;
    }

    /**
     * Returns the human-readable name of the preset.
     */
    public String getPresetName(String preset) {
        String normalized = normalizePreset(preset);
        return switch (normalized) {
            case PRESET_CIRCULAR_DEMO -> "Circular Feedback Loop Demo";
            case PRESET_ECOMMERCE_DAG -> "E-Commerce Architecture";
            default -> "Demonstration Topology";
        };
    }

    /**
     * Returns the description of the preset.
     */
    public String getPresetDescription(String preset) {
        String normalized = normalizePreset(preset);
        return switch (normalized) {
            case PRESET_CIRCULAR_DEMO ->
                    "E-Commerce topology containing a circular dependency between Payment Service and Notification Service";
            case PRESET_ECOMMERCE_DAG ->
                    "Standard 8-service microservices topology (Acyclic DAG)";
            default -> "Demonstration dependency graph topology";
        };
    }

    /**
     * Retrieves the service catalog for the given preset.
     */
    public List<ServiceDto> getServices(String preset) {
        normalizePreset(preset);
        return Collections.unmodifiableList(STANDARD_SERVICES);
    }

    /**
     * Finds metadata for a specific service ID.
     */
    public Optional<ServiceDto> getServiceMetadata(String preset, String serviceId) {
        normalizePreset(preset);
        if (serviceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SERVICE_MAP.get(serviceId.trim()));
    }

    /**
     * Builds and returns a new {@link DependencyGraph} for the specified preset.
     *
     * @param preset the preset identifier
     * @return a populated {@link DependencyGraph}
     */
    public DependencyGraph getGraph(String preset) {
        String normalized = normalizePreset(preset);
        DependencyGraph graph = new DefaultDependencyGraph();

        // Register all standard services
        for (ServiceDto service : STANDARD_SERVICES) {
            graph.addService(service.id());
        }

        // Establish the 10 core DAG dependencies
        // Convention: source -> target means source depends on target
        graph.addDependency("Storefront Web", "Checkout Service");
        graph.addDependency("Storefront Web", "Auth Service");
        graph.addDependency("Checkout Service", "Order Service");
        graph.addDependency("Checkout Service", "Payment Service");
        graph.addDependency("Order Service", "Payment Service");
        graph.addDependency("Order Service", "Inventory Service");
        graph.addDependency("Order Service", "Audit Logger");
        graph.addDependency("Payment Service", "Notification Service");
        graph.addDependency("Payment Service", "Audit Logger");
        graph.addDependency("Auth Service", "Audit Logger");

        // Preset 2 adds the circular feedback back-edge
        if (PRESET_CIRCULAR_DEMO.equals(normalized)) {
            graph.addDependency("Notification Service", "Payment Service");
        }

        return graph;
    }
}
