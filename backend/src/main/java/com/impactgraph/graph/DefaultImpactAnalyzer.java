package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;

import java.util.*;

/**
 * Standard implementation of {@link ImpactAnalyzer} utilizing Breadth-First Search (BFS)
 * across incoming/reverse dependency edges with visited-node tracking.
 * <p>
 * <b>Edge-Direction Handling:</b>
 * In ImpactGraph, {@code A -> B} signifies that service {@code A} depends on service {@code B}.
 * Consequently, when {@code B} changes, the blast radius affects services that depend on {@code B}.
 * Impact analysis therefore traverses <i>incoming/reverse edges</i> from the changed node.
 * <p>
 * <b>Cycle Safety:</b>
 * A {@code visited} set tracks all visited nodes, initialized with the changed service itself.
 * If a cycle exists (e.g. {@code A -> B -> A}), the traversal encounters the already-visited node
 * and skips re-enqueuing it. This guarantees that:
 * <ul>
 *   <li>The algorithm strictly terminates in {@code O(V + E)} time without infinite loops.</li>
 *   <li>Each affected service appears exactly once in the result set (no duplicates).</li>
 *   <li>The computed path represents the shortest simple acyclic dependency chain to the changed service.</li>
 *   <li>The changed service is never reported as its own affected dependent.</li>
 * </ul>
 * <p>
 * <b>Determinism:</b>
 * At each level of BFS, neighboring dependents are sorted lexicographically before insertion into the
 * queue, ensuring 100% reproducible traversal orders and path assignments across runs.
 */
public class DefaultImpactAnalyzer implements ImpactAnalyzer {

    @Override
    public ImpactAnalysisResult analyzeImpact(DependencyGraph graph, String changedServiceId) {
        if (graph == null) {
            throw new IllegalArgumentException("Dependency graph cannot be null");
        }
        if (changedServiceId == null || changedServiceId.isBlank()) {
            throw new InvalidServiceIdException("Changed service ID cannot be null or blank");
        }

        String target = changedServiceId.trim();
        if (!graph.hasService(target)) {
            throw new UnknownServiceException(
                    "Changed service '" + target + "' is not registered in the graph");
        }

        // Visited set tracks all seen services. Initializing with target ensures that
        // the changed service is never added to the affected dependents list and self-cycles terminate.
        Set<String> visited = new HashSet<>();
        visited.add(target);

        // Maps dependent -> the next node on the dependency chain towards target
        Map<String, String> nextInPath = new HashMap<>();

        Queue<String> queue = new ArrayDeque<>();
        queue.add(target);

        Set<String> affectedServices = new TreeSet<>();
        Map<String, ImpactPath> impactPaths = new LinkedHashMap<>();

        while (!queue.isEmpty()) {
            String current = queue.poll();

            // Dependents of current are services having an edge: dependent -> current
            List<String> dependents = new ArrayList<>(graph.getDependents(current));
            Collections.sort(dependents);

            for (String dependent : dependents) {
                if (visited.add(dependent)) {
                    nextInPath.put(dependent, current);
                    affectedServices.add(dependent);
                    queue.add(dependent);
                }
            }
        }

        // Reconstruct paths from each affected service to the changed service
        for (String affected : affectedServices) {
            List<String> pathNodes = new ArrayList<>();
            String curr = affected;
            pathNodes.add(curr);
            while (!curr.equals(target)) {
                curr = nextInPath.get(curr);
                pathNodes.add(curr);
            }
            ImpactPath impactPath = new ImpactPath(affected, target, pathNodes);
            impactPaths.put(affected, impactPath);
        }

        return new ImpactAnalysisResult(target, affectedServices, impactPaths);
    }
}
