package com.impactgraph.dto;

/**
 * Data transfer object representing a software service node with rich metadata.
 *
 * @param id          unique identifier of the service (matches graph node ID)
 * @param label       display label of the service
 * @param type        architectural type (e.g. "Core Domain", "Frontend / Gateway")
 * @param tier        architectural tier (e.g. "Domain Tier", "Client Tier")
 * @param icon        icon identifier (e.g. "globe", "cart", "card")
 * @param description brief summary of the service's responsibilities
 */
public record ServiceDto(
        String id,
        String label,
        String type,
        String tier,
        String icon,
        String description
) {
}
