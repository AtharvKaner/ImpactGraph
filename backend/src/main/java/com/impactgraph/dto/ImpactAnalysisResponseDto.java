package com.impactgraph.dto;

import java.util.List;
import java.util.Set;

/**
 * Data transfer object encapsulating the results of a change impact blast-radius analysis.
 *
 * @param changedServiceId           the root service undergoing the simulated change
 * @param affectedCount              total count of directly and transitively affected services
 * @param affectedServiceIds         set of all affected service IDs
 * @param directAffectedServices     set of services directly depending on the changed service (distance == 1)
 * @param transitiveAffectedServices set of services transitively depending on the changed service (distance &gt; 1)
 * @param impactPaths                deterministic list of explanatory dependency paths
 * @param cycleResult                cycle detection findings for the analyzed graph topology
 */
public record ImpactAnalysisResponseDto(
        String changedServiceId,
        int affectedCount,
        Set<String> affectedServiceIds,
        Set<String> directAffectedServices,
        Set<String> transitiveAffectedServices,
        List<ImpactPathDto> impactPaths,
        CycleDetectionResponseDto cycleResult
) {
}
