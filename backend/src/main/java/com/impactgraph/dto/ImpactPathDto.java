package com.impactgraph.dto;

import com.impactgraph.graph.ImpactPath;

import java.util.List;

/**
 * Data transfer object encapsulating a dependency path connecting an affected service
 * to the changed service.
 * <p>
 * <b>Direction Convention:</b> {@code [affectedService, intermediate_1, ..., changedService]}
 * where each hop {@code A -> B} denotes that A depends on B.
 *
 * @param affectedServiceId the service experiencing downstream impact
 * @param changedServiceId  the root service where the simulated change originates
 * @param distance          dependency hop distance (1 for direct, &gt;1 for transitive)
 * @param isDirect          true if distance is 1
 * @param isTransitive      true if distance is &gt; 1
 * @param path              ordered list of service IDs from affected service to changed service
 * @param pathString        formatted arrow string representation
 */
public record ImpactPathDto(
        String affectedServiceId,
        String changedServiceId,
        int distance,
        boolean isDirect,
        boolean isTransitive,
        List<String> path,
        String pathString
) {
    public static ImpactPathDto from(ImpactPath ip) {
        return new ImpactPathDto(
                ip.affectedServiceId(),
                ip.changedServiceId(),
                ip.distance(),
                ip.isDirect(),
                ip.isTransitive(),
                ip.path(),
                ip.toPathString()
        );
    }
}
