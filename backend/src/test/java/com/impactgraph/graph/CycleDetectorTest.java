package com.impactgraph.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Directed Cycle Detection Tests")
class CycleDetectorTest {

    private CycleDetector cycleDetector;
    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        cycleDetector = new DefaultCycleDetector();
        graph = DependencyGraph.create();
    }

    @Test
    @DisplayName("Should detect no cycles in an empty graph")
    void testEmptyGraphNoCycles() {
        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertFalse(result.hasCycle());
        assertEquals(0, result.getCycleCount());
        assertTrue(result.getFirstCycle().isEmpty());
    }

    @Test
    @DisplayName("Should detect no cycles in a directed acyclic graph (DAG)")
    void testAcyclicGraph() {
        graph.addService("A");
        graph.addService("B");
        graph.addService("C");
        graph.addService("D");

        graph.addDependency("A", "B");
        graph.addDependency("B", "C");
        graph.addDependency("A", "C");
        graph.addDependency("C", "D");

        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertFalse(result.hasCycle());
        assertEquals(0, result.getCycleCount());
    }

    @Test
    @DisplayName("Should detect a simple 2-node cycle (A -> B -> A)")
    void testSimpleTwoNodeCycle() {
        graph.addService("ServiceA");
        graph.addService("ServiceB");

        graph.addDependency("ServiceA", "ServiceB");
        graph.addDependency("ServiceB", "ServiceA");

        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertTrue(result.hasCycle());
        assertTrue(result.getCycleCount() >= 1);

        DirectedCycle cycle = result.getFirstCycle().orElseThrow();
        assertEquals(2, cycle.length());
        assertEquals("ServiceA", cycle.nodes().getFirst());
        assertEquals("ServiceA", cycle.nodes().getLast());
    }

    @Test
    @DisplayName("Should detect a 3-node cycle (A -> B -> C -> A)")
    void testThreeNodeCycle() {
        graph.addService("A");
        graph.addService("B");
        graph.addService("C");

        graph.addDependency("A", "B");
        graph.addDependency("B", "C");
        graph.addDependency("C", "A");

        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertTrue(result.hasCycle());
        assertEquals(1, result.getCycleCount());

        DirectedCycle cycle = result.getFirstCycle().orElseThrow();
        assertEquals(3, cycle.length());
        assertEquals(List.of("A", "B", "C", "A"), cycle.nodes());
        assertEquals("A -> B -> C -> A", cycle.toCycleString());
    }

    @Test
    @DisplayName("Should detect a self-loop cycle (A -> A)")
    void testSelfLoopCycle() {
        graph.addService("SelfRef");
        graph.addDependency("SelfRef", "SelfRef");

        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertTrue(result.hasCycle());
        assertEquals(1, result.getCycleCount());

        DirectedCycle cycle = result.getFirstCycle().orElseThrow();
        assertEquals(1, cycle.length());
        assertEquals(List.of("SelfRef", "SelfRef"), cycle.nodes());
        assertEquals("SelfRef -> SelfRef", cycle.toCycleString());
    }

    @Test
    @DisplayName("Should detect cycle located in a disconnected component")
    void testCycleInDisconnectedComponent() {
        // Component 1 (Acyclic): X -> Y -> Z
        graph.addService("X");
        graph.addService("Y");
        graph.addService("Z");
        graph.addDependency("X", "Y");
        graph.addDependency("Y", "Z");

        // Component 2 (Cyclic): C1 -> C2 -> C1
        graph.addService("C1");
        graph.addService("C2");
        graph.addDependency("C1", "C2");
        graph.addDependency("C2", "C1");

        // Component 3 (Isolated)
        graph.addService("Isolated");

        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertTrue(result.hasCycle());
        assertEquals(1, result.getCycleCount());

        DirectedCycle cycle = result.getFirstCycle().orElseThrow();
        assertEquals("C1 -> C2 -> C1", cycle.toCycleString());
    }

    @Test
    @DisplayName("Should detect multiple cycles across disconnected components")
    void testMultipleCyclesAcrossComponents() {
        // Component 1: A -> B -> A
        graph.addService("A");
        graph.addService("B");
        graph.addDependency("A", "B");
        graph.addDependency("B", "A");

        // Component 2: M -> N -> M
        graph.addService("M");
        graph.addService("N");
        graph.addDependency("M", "N");
        graph.addDependency("N", "M");

        CycleDetectionResult result = cycleDetector.detectCycles(graph);
        assertTrue(result.hasCycle());
        assertEquals(2, result.getCycleCount());
    }
}
