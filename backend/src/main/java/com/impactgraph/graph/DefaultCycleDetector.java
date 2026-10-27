package com.impactgraph.graph;

import java.util.*;

/**
 * Standard implementation of {@link CycleDetector} using Depth-First Search (DFS)
 * with 3-color state tracking (WHITE = unvisited, GRAY = active recursion stack, BLACK = fully visited).
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li><b>Disconnected Components:</b> Iterates through all registered services in deterministic order
 *       to guarantee that disconnected graph components are all inspected.</li>
 *   <li><b>Cycle Extraction:</b> When a back-edge to an active (GRAY) node is detected, the full cycle
 *       sub-path is extracted and canonicalized to prevent reporting duplicated rotations of the same cycle.</li>
 *   <li><b>Robustness:</b> Operates in {@code O(V + E)} time and handles arbitrary directed graphs,
 *       including self-loops and multi-node cycles.</li>
 * </ul>
 */
public class DefaultCycleDetector implements CycleDetector {

    private static final int STATE_WHITE = 0; // Unvisited
    private static final int STATE_GRAY = 1;  // In current DFS recursion stack
    private static final int STATE_BLACK = 2; // Completed / backtrack finished

    @Override
    public CycleDetectionResult detectCycles(DependencyGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Dependency graph cannot be null");
        }
        if (graph.isEmpty()) {
            return CycleDetectionResult.noCycles();
        }

        Map<String, Integer> state = new HashMap<>();
        List<String> stack = new ArrayList<>();
        Set<String> seenCanonicalCycles = new LinkedHashSet<>();
        List<DirectedCycle> detectedCycles = new ArrayList<>();

        // Sort all services alphabetically to ensure completely deterministic outer loop traversal
        List<String> allServices = new ArrayList<>(graph.getServices());
        Collections.sort(allServices);

        for (String serviceId : allServices) {
            if (state.getOrDefault(serviceId, STATE_WHITE) == STATE_WHITE) {
                dfs(serviceId, graph, state, stack, seenCanonicalCycles, detectedCycles);
            }
        }

        return CycleDetectionResult.ofCycles(detectedCycles);
    }

    private void dfs(
            String current,
            DependencyGraph graph,
            Map<String, Integer> state,
            List<String> stack,
            Set<String> seenCanonicalCycles,
            List<DirectedCycle> detectedCycles
    ) {
        state.put(current, STATE_GRAY);
        stack.add(current);

        // Sort outgoing dependencies for deterministic traversal
        List<String> dependencies = new ArrayList<>(graph.getDependencies(current));
        Collections.sort(dependencies);

        for (String target : dependencies) {
            int targetState = state.getOrDefault(target, STATE_WHITE);

            if (targetState == STATE_GRAY) {
                // Back-edge detected: cycle exists from target to current back to target
                int startIndex = stack.indexOf(target);
                if (startIndex != -1) {
                    List<String> cycleNodes = new ArrayList<>(stack.subList(startIndex, stack.size()));
                    cycleNodes.add(target);

                    DirectedCycle canonical = canonicalize(cycleNodes);
                    if (seenCanonicalCycles.add(canonical.toCycleString())) {
                        detectedCycles.add(new DirectedCycle(cycleNodes));
                    }
                }
            } else if (targetState == STATE_WHITE) {
                dfs(target, graph, state, stack, seenCanonicalCycles, detectedCycles);
            }
        }

        stack.removeLast();
        state.put(current, STATE_BLACK);
    }

    /**
     * Canonicalizes a cycle by rotating it so that the lexicographically smallest node appears first.
     * For example, {@code [B, C, A, B]} rotates to {@code [A, B, C, A]}.
     */
    private DirectedCycle canonicalize(List<String> cycle) {
        List<String> base = cycle.subList(0, cycle.size() - 1);
        int n = base.size();
        int minIndex = 0;
        String minVal = base.getFirst();

        for (int i = 1; i < n; i++) {
            if (base.get(i).compareTo(minVal) < 0) {
                minVal = base.get(i);
                minIndex = i;
            }
        }

        List<String> canonical = new ArrayList<>(n + 1);
        for (int i = 0; i < n; i++) {
            canonical.add(base.get((minIndex + i) % n));
        }
        canonical.add(minVal);
        return new DirectedCycle(canonical);
    }
}
