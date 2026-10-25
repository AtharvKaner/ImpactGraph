package com.impactgraph.graph;

import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Change-Impact Analysis Tests")
class ImpactAnalyzerTest {

    private ImpactAnalyzer analyzer;
    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        analyzer = new DefaultImpactAnalyzer();
        graph = DependencyGraph.create();
    }

    /**
     * Scenario 1: A simple dependency chain.
     * OrderService -> PaymentService -> NotificationService
     */
    @Test
    @DisplayName("Scenario 1: Simple dependency chain (Order -> Payment -> Notification)")
    void testScenario1_SimpleDependencyChain() {
        graph.addService("OrderService");
        graph.addService("PaymentService");
        graph.addService("NotificationService");

        graph.addDependency("OrderService", "PaymentService");
        graph.addDependency("PaymentService", "NotificationService");

        // When NotificationService changes, both PaymentService and OrderService depend on it
        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "NotificationService");

        assertEquals("NotificationService", result.changedServiceId());
        assertEquals(2, result.getAffectedCount());
        assertTrue(result.isAffected("PaymentService"));
        assertTrue(result.isAffected("OrderService"));
        assertFalse(result.isAffected("NotificationService"), "Changed service is not its own dependent");

        // Paths follow original dependency direction: affected -> ... -> changed
        ImpactPath paymentPath = result.getImpactPath("PaymentService").orElseThrow();
        assertEquals(List.of("PaymentService", "NotificationService"), paymentPath.path());
        assertEquals(1, paymentPath.distance());
        assertTrue(paymentPath.isDirect());
        assertEquals("PaymentService -> NotificationService", paymentPath.toPathString());

        ImpactPath orderPath = result.getImpactPath("OrderService").orElseThrow();
        assertEquals(List.of("OrderService", "PaymentService", "NotificationService"), orderPath.path());
        assertEquals(2, orderPath.distance());
        assertTrue(orderPath.isTransitive());
        assertEquals("OrderService -> PaymentService -> NotificationService", orderPath.toPathString());
    }

    /**
     * Scenario 2: Multiple direct dependents of one service.
     * Order Service -> Payment Service
     * Checkout Service -> Payment Service
     * Payment Service -> Notification Service
     */
    @Test
    @DisplayName("Scenario 2: Multiple direct dependents of one service (prompt example)")
    void testScenario2_MultipleDirectDependents() {
        graph.addService("Order Service");
        graph.addService("Checkout Service");
        graph.addService("Payment Service");
        graph.addService("Notification Service");

        graph.addDependency("Order Service", "Payment Service");
        graph.addDependency("Checkout Service", "Payment Service");
        graph.addDependency("Payment Service", "Notification Service");

        // Payment Service changes: affected should be Order Service and Checkout Service
        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "Payment Service");

        assertEquals("Payment Service", result.changedServiceId());
        assertEquals(Set.of("Checkout Service", "Order Service"), result.affectedServiceIds());
        assertEquals(2, result.getAffectedCount());
        assertFalse(result.isAffected("Notification Service"), "Notification Service is a dependency, not dependent");
        assertFalse(result.isAffected("Payment Service"), "Payment Service cannot be in its own affected list");

        assertEquals(List.of("Checkout Service", "Payment Service"), result.getPath("Checkout Service"));
        assertEquals(List.of("Order Service", "Payment Service"), result.getPath("Order Service"));

        assertEquals(Set.of("Checkout Service", "Order Service"), result.getDirectAffectedServices());
        assertTrue(result.getTransitiveAffectedServices().isEmpty());
    }

    /**
     * Scenario 3: A transitive impact path.
     * WebApp -> ApiGateway -> OrderService -> InventoryService -> DatabaseService
     */
    @Test
    @DisplayName("Scenario 3: Transitive impact path across a 5-node chain")
    void testScenario3_TransitiveImpactPath() {
        graph.addService("WebApp");
        graph.addService("ApiGateway");
        graph.addService("OrderService");
        graph.addService("InventoryService");
        graph.addService("DatabaseService");

        graph.addDependency("WebApp", "ApiGateway");
        graph.addDependency("ApiGateway", "OrderService");
        graph.addDependency("OrderService", "InventoryService");
        graph.addDependency("InventoryService", "DatabaseService");

        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "DatabaseService");

        assertEquals(4, result.getAffectedCount());
        assertEquals(Set.of("InventoryService"), result.getDirectAffectedServices());
        assertEquals(Set.of("ApiGateway", "OrderService", "WebApp"), result.getTransitiveAffectedServices());

        ImpactPath webAppPath = result.getImpactPath("WebApp").orElseThrow();
        assertEquals(List.of("WebApp", "ApiGateway", "OrderService", "InventoryService", "DatabaseService"),
                webAppPath.path());
        assertEquals(4, webAppPath.distance());
        assertTrue(webAppPath.isTransitive());
    }

    /**
     * Scenario 4: A branching graph (diamond and convergent dependencies).
     * ClientA -> ServiceB -> Target
     * ClientA -> ServiceC -> Target
     */
    @Test
    @DisplayName("Scenario 4: Branching diamond graph with multiple paths to target")
    void testScenario4_BranchingGraph() {
        graph.addService("ClientA");
        graph.addService("ServiceB");
        graph.addService("ServiceC");
        graph.addService("Target");

        graph.addDependency("ClientA", "ServiceB");
        graph.addDependency("ClientA", "ServiceC");
        graph.addDependency("ServiceB", "Target");
        graph.addDependency("ServiceC", "Target");

        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "Target");

        assertEquals(3, result.getAffectedCount());
        assertEquals(Set.of("ClientA", "ServiceB", "ServiceC"), result.affectedServiceIds());
        assertEquals(Set.of("ServiceB", "ServiceC"), result.getDirectAffectedServices());
        assertEquals(Set.of("ClientA"), result.getTransitiveAffectedServices());

        // Deterministic path: due to alphabetical tie-breaking, ServiceB is visited before ServiceC
        ImpactPath clientAPath = result.getImpactPath("ClientA").orElseThrow();
        assertEquals(List.of("ClientA", "ServiceB", "Target"), clientAPath.path());
        assertEquals(2, clientAPath.distance());
    }

    /**
     * Scenario 5: An isolated service.
     */
    @Test
    @DisplayName("Scenario 5: Isolated service with no dependencies or dependents")
    void testScenario5_IsolatedService() {
        graph.addService("IsolatedService");
        graph.addService("ActiveServiceA");
        graph.addService("ActiveServiceB");
        graph.addDependency("ActiveServiceA", "ActiveServiceB");

        // When IsolatedService changes, nothing is affected
        ImpactAnalysisResult isolatedResult = analyzer.analyzeImpact(graph, "IsolatedService");
        assertFalse(isolatedResult.hasImpact());
        assertEquals(0, isolatedResult.getAffectedCount());
        assertTrue(isolatedResult.affectedServiceIds().isEmpty());

        // When ActiveServiceB changes, IsolatedService is not affected
        ImpactAnalysisResult activeResult = analyzer.analyzeImpact(graph, "ActiveServiceB");
        assertEquals(Set.of("ActiveServiceA"), activeResult.affectedServiceIds());
        assertFalse(activeResult.isAffected("IsolatedService"));
    }

    /**
     * Scenario 6: Disconnected graph components.
     * Component 1: Web -> Api -> Database
     * Component 2: Worker -> Queue
     * Component 3: StandaloneAudit
     */
    @Test
    @DisplayName("Scenario 6: Disconnected graph components maintain isolation")
    void testScenario6_DisconnectedComponents() {
        graph.addService("Web");
        graph.addService("Api");
        graph.addService("Database");
        graph.addDependency("Web", "Api");
        graph.addDependency("Api", "Database");

        graph.addService("Worker");
        graph.addService("Queue");
        graph.addDependency("Worker", "Queue");

        graph.addService("StandaloneAudit");

        // Impact on Database in Component 1 affects only Web and Api
        ImpactAnalysisResult dbResult = analyzer.analyzeImpact(graph, "Database");
        assertEquals(Set.of("Api", "Web"), dbResult.affectedServiceIds());
        assertFalse(dbResult.isAffected("Worker"));
        assertFalse(dbResult.isAffected("Queue"));
        assertFalse(dbResult.isAffected("StandaloneAudit"));

        // Impact on Queue in Component 2 affects only Worker
        ImpactAnalysisResult queueResult = analyzer.analyzeImpact(graph, "Queue");
        assertEquals(Set.of("Worker"), queueResult.affectedServiceIds());
        assertFalse(queueResult.isAffected("Database"));
        assertFalse(queueResult.isAffected("Web"));
    }

    /**
     * Scenario 7: A graph containing a directed cycle.
     * Cycle: A -> B -> C -> A
     * Plus dependency: C -> TargetDB
     */
    @Test
    @DisplayName("Scenario 7: Graph with directed cycle terminates cleanly without infinite traversal")
    void testScenario7_GraphWithDirectedCycle() {
        graph.addService("ServiceA");
        graph.addService("ServiceB");
        graph.addService("ServiceC");
        graph.addService("TargetDB");

        // Cycle: A depends on B, B depends on C, C depends on A
        graph.addDependency("ServiceA", "ServiceB");
        graph.addDependency("ServiceB", "ServiceC");
        graph.addDependency("ServiceC", "ServiceA");

        // C also depends on TargetDB
        graph.addDependency("ServiceC", "TargetDB");

        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "TargetDB");

        assertEquals(3, result.getAffectedCount());
        assertEquals(Set.of("ServiceA", "ServiceB", "ServiceC"), result.affectedServiceIds());

        // Each affected service has a finite simple path with no duplicate nodes
        for (String affected : result.affectedServiceIds()) {
            ImpactPath impactPath = result.getImpactPath(affected).orElseThrow();
            List<String> nodes = impactPath.path();
            assertEquals(nodes.size(), Set.copyOf(nodes).size(), "Path must not contain duplicate cycle nodes");
            assertEquals(affected, nodes.getFirst());
            assertEquals("TargetDB", nodes.getLast());
        }

        // Exact paths
        assertEquals(List.of("ServiceC", "TargetDB"), result.getPath("ServiceC"));
        assertEquals(List.of("ServiceB", "ServiceC", "TargetDB"), result.getPath("ServiceB"));
        assertEquals(List.of("ServiceA", "ServiceB", "ServiceC", "TargetDB"), result.getPath("ServiceA"));
    }

    @Test
    @DisplayName("Scenario 7 (Self-Cycle): Node with self-dependency does not report self as affected")
    void testScenario7_SelfDependencyCycle() {
        graph.addService("SelfRefService");
        graph.addDependency("SelfRefService", "SelfRefService");

        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "SelfRefService");
        assertFalse(result.hasImpact());
        assertEquals(0, result.getAffectedCount());
        assertFalse(result.isAffected("SelfRefService"));
    }

    @Test
    @DisplayName("Scenario 7 (Cycle Returning to Target): Cycle returning to changed service never counts target as affected")
    void testScenario7_CycleReturningToChangedTarget() {
        // Payment depends on Notification, and Notification depends on Payment (2-cycle)
        // Order also depends on Payment
        graph.addService("Payment Service");
        graph.addService("Notification Service");
        graph.addService("Order Service");

        graph.addDependency("Payment Service", "Notification Service");
        graph.addDependency("Notification Service", "Payment Service");
        graph.addDependency("Order Service", "Payment Service");

        // When Payment Service changes:
        // Dependents are Notification Service (direct) and Order Service (direct)
        // Notification Service's dependent is Payment Service, which is the changed target!
        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "Payment Service");

        assertEquals("Payment Service", result.changedServiceId());
        assertEquals(2, result.getAffectedCount());
        assertEquals(Set.of("Notification Service", "Order Service"), result.affectedServiceIds());
        assertEquals(Set.of("Notification Service", "Order Service"), result.getDirectAffectedServices());
        assertTrue(result.getTransitiveAffectedServices().isEmpty());

        // Target must NEVER be counted as its own affected service
        assertFalse(result.isAffected("Payment Service"), "Payment Service cannot be affected by itself");
        assertFalse(result.affectedServiceIds().contains("Payment Service"));
        assertFalse(result.getDirectAffectedServices().contains("Payment Service"));
        assertFalse(result.getTransitiveAffectedServices().contains("Payment Service"));
        assertTrue(result.getImpactPath("Payment Service").isEmpty());
    }

    /**
     * Scenario 8: Duplicate dependency edges.
     */
    @Test
    @DisplayName("Scenario 8: Duplicate dependency edges produce exact, deduplicated impact results")
    void testScenario8_DuplicateDependencyEdges() {
        graph.addService("ServiceX");
        graph.addService("ServiceY");

        graph.addDependency("ServiceX", "ServiceY");
        graph.addDependency("ServiceX", "ServiceY"); // Duplicate edge
        graph.addDependency("ServiceX", "ServiceY"); // Triplicate edge

        ImpactAnalysisResult result = analyzer.analyzeImpact(graph, "ServiceY");

        assertEquals(1, result.getAffectedCount());
        assertEquals(Set.of("ServiceX"), result.affectedServiceIds());
        assertEquals(List.of("ServiceX", "ServiceY"), result.getPath("ServiceX"));
    }

    /**
     * Scenario 9: An unknown changed service ID.
     */
    @Test
    @DisplayName("Scenario 9: Throws UnknownServiceException when changed service ID is not registered")
    void testScenario9_UnknownChangedServiceId() {
        graph.addService("ExistingService");

        UnknownServiceException ex = assertThrows(UnknownServiceException.class,
                () -> analyzer.analyzeImpact(graph, "NonExistentService"));
        assertTrue(ex.getMessage().contains("NonExistentService"));
    }

    /**
     * Scenario 10: An edge referencing an unknown service.
     */
    @Test
    @DisplayName("Scenario 10: Adding edge with unknown service fails and missing service is not silently created")
    void testScenario10_EdgeReferencingUnknownService() {
        graph.addService("KnownService");

        assertThrows(UnknownServiceException.class,
                () -> graph.addDependency("KnownService", "UnknownTarget"));
        assertThrows(UnknownServiceException.class,
                () -> graph.addDependency("UnknownSource", "KnownService"));

        assertFalse(graph.hasService("UnknownTarget"));
        assertFalse(graph.hasService("UnknownSource"));
    }

    /**
     * Scenario 11: Invalid or blank service IDs.
     */
    @Test
    @DisplayName("Scenario 11: Invalid or blank service IDs throw InvalidServiceIdException")
    void testScenario11_InvalidOrBlankServiceIds() {
        graph.addService("ValidService");

        assertThrows(InvalidServiceIdException.class, () -> analyzer.analyzeImpact(graph, null));
        assertThrows(InvalidServiceIdException.class, () -> analyzer.analyzeImpact(graph, ""));
        assertThrows(InvalidServiceIdException.class, () -> analyzer.analyzeImpact(graph, "   \t "));
    }

    /**
     * Scenario 12: An empty graph.
     */
    @Test
    @DisplayName("Scenario 12: Empty graph throws UnknownServiceException on impact analysis")
    void testScenario12_EmptyGraph() {
        DependencyGraph emptyGraph = DependencyGraph.empty();
        assertTrue(emptyGraph.isEmpty());

        UnknownServiceException ex = assertThrows(UnknownServiceException.class,
                () -> analyzer.analyzeImpact(emptyGraph, "AnyService"));
        assertTrue(ex.getMessage().contains("AnyService"));
    }
}
