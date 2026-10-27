package com.impactgraph.dto;

import com.impactgraph.graph.CycleDetectionResult;

import java.util.List;

/**
 * Data transfer object encapsulating the full cycle detection response.
 *
 * @param hasCycle   true if at least one directed cycle was detected
 * @param cycleCount number of detected cycles
 * @param cycles     list of detected cycles
 */
public record CycleDetectionResponseDto(
        boolean hasCycle,
        int cycleCount,
        List<CycleDto> cycles
) {
    public static CycleDetectionResponseDto from(CycleDetectionResult result) {
        List<CycleDto> cycleDtos = result.cycles().stream()
                .map(CycleDto::from)
                .toList();
        return new CycleDetectionResponseDto(result.hasCycle(), result.getCycleCount(), cycleDtos);
    }
}
