package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Directed Dependency Graph Tests")
class DependencyGraphTest {

    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        graph = DependencyGraph.create();
    }

    @Test
    @DisplayName("Should successfully register and retrieve services")
    void testRegisterServices() {
        graph.addService("OrderService");
        graph.addService("PaymentService");

        assertEquals(2, graph.getServiceCount());
        assertTrue(graph.hasService("OrderService"));
        assertTrue(graph.hasService("PaymentService"));
        assertFalse(graph.hasService("NotificationService"));
        assertFalse(graph.isEmpty());
    }

    @Test
    @DisplayName("Should reject null, empty, or blank service IDs during registration")
    void testRegisterInvalidServiceIds() {
        assertThrows(InvalidServiceIdException.class, () -> graph.addService(null));
        assertThrows(InvalidServiceIdException.class, () -> graph.addService(""));
        assertThrows(InvalidServiceIdException.class, () -> graph.addService("   \t\n "));
    }

    @Test
    @DisplayName("Should register directed dependency edge and maintain forward and reverse adjacencies")
    void testAddDependencyEdge() {
        // Convention: OrderService -> PaymentService means OrderService depends on PaymentService
        graph.addService("OrderService");
        graph.addService("PaymentService");
        graph.addDependency("OrderService", "PaymentService");

        assertEquals(1, graph.getEdgeCount());
        assertTrue(graph.hasEdge("OrderService", "PaymentService"));
        assertFalse(graph.hasEdge("PaymentService", "OrderService"));

        // Forward: OrderService depends on PaymentService
        Set<String> dependencies = graph.getDependencies("OrderService");
        assertEquals(Set.of("PaymentService"), dependencies);

        // Reverse: PaymentService is depended upon by OrderService
        Set<String> dependents = graph.getDependents("PaymentService");
        assertEquals(Set.of("OrderService"), dependents);
    }

    @Test
    @DisplayName("Should throw UnknownServiceException when dependent service is not registered")
    void testAddDependencyUnknownDependent() {
        graph.addService("PaymentService");

        UnknownServiceException ex = assertThrows(UnknownServiceException.class,
                () -> graph.addDependency("UnknownService", "PaymentService"));
        assertTrue(ex.getMessage().contains("UnknownService"));
        assertFalse(graph.hasService("UnknownService"), "Missing service must not be silently created");
    }

    @Test
    @DisplayName("Should throw UnknownServiceException when target dependency service is not registered")
    void testAddDependencyUnknownDependency() {
        graph.addService("OrderService");

        UnknownServiceException ex = assertThrows(UnknownServiceException.class,
                () -> graph.addDependency("OrderService", "UnknownTarget"));
        assertTrue(ex.getMessage().contains("UnknownTarget"));
        assertFalse(graph.hasService("UnknownTarget"), "Missing service must not be silently created");
    }

    @Test
    @DisplayName("Should reject invalid or blank service IDs when adding dependency")
    void testAddDependencyInvalidIds() {
        graph.addService("ValidService");

        assertThrows(InvalidServiceIdException.class, () -> graph.addDependency(null, "ValidService"));
        assertThrows(InvalidServiceIdException.class, () -> graph.addDependency("  ", "ValidService"));
        assertThrows(InvalidServiceIdException.class, () -> graph.addDependency("ValidService", null));
        assertThrows(InvalidServiceIdException.class, () -> graph.addDependency("ValidService", ""));
    }

    @Test
    @DisplayName("Should prevent duplicate edges from increasing edge count")
    void testPreventDuplicateEdges() {
        graph.addService("A");
        graph.addService("B");

        graph.addDependency("A", "B");
        graph.addDependency("A", "B"); // Duplicate edge
        graph.addDependency(new DependencyEdge("A", "B")); // Duplicate edge via record

        assertEquals(1, graph.getEdgeCount());
        assertEquals(Set.of("B"), graph.getDependencies("A"));
        assertEquals(Set.of("A"), graph.getDependents("B"));
    }

    @Test
    @DisplayName("Should correctly construct graph via fluent Builder")
    void testGraphBuilder() {
        DependencyGraph built = DependencyGraph.builder()
                .addServices(List.of("ServiceA", "ServiceB", "ServiceC"))
                .addDependency("ServiceA", "ServiceB")
                .addDependency(DependencyEdge.of("ServiceB", "ServiceC"))
                .build();

        assertEquals(3, built.getServiceCount());
        assertEquals(2, built.getEdgeCount());
        assertTrue(built.hasEdge("ServiceA", "ServiceB"));
        assertTrue(built.hasEdge("ServiceB", "ServiceC"));
    }

    @Test
    @DisplayName("Should construct graph using static factory DependencyGraph.of")
    void testStaticFactoryOf() {
        List<String> services = List.of("A", "B");
        List<DependencyEdge> edges = List.of(DependencyEdge.of("A", "B"));

        DependencyGraph built = DependencyGraph.of(services, edges);
        assertEquals(2, built.getServiceCount());
        assertEquals(1, built.getEdgeCount());
    }

    @Test
    @DisplayName("Returned sets should be unmodifiable to protect encapsulation")
    void testEncapsulationUnmodifiable() {
        graph.addService("A");
        graph.addService("B");
        graph.addDependency("A", "B");

        assertThrows(UnsupportedOperationException.class, () -> graph.getServices().add("C"));
        assertThrows(UnsupportedOperationException.class, () -> graph.getEdges().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.getDependencies("A").add("X"));
        assertThrows(UnsupportedOperationException.class, () -> graph.getDependents("B").add("Y"));
    }
}
