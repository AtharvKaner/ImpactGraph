package com.impactgraph.graph;

import com.impactgraph.dto.request.DependencyRequest;
import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.request.ServiceRequest;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.ServiceRepository;
import com.impactgraph.service.DependencyService;
import com.impactgraph.service.ProjectService;
import com.impactgraph.service.ServiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration and compatibility tests verifying that the Core Backend
 * and Data Model (Arjun's deliverable) seamlessly feed the Graph Engine and
 * Impact Analysis components (Atharv's deliverable) without friction.
 */
@SpringBootTest
@DisplayName("Core Data Model <-> Graph Engine Compatibility Tests")
class GraphEngineCompatibilityTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private DependencyService dependencyService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    @Autowired
    private ImpactAnalyzer impactAnalyzer;

    @Autowired
    private CycleDetector cycleDetector;

    @BeforeEach
    void cleanDatabase() {
        dependencyRepository.deleteAll();
        serviceRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    @DisplayName("Should build DependencyGraph from JPA data model and accurately compute impact analysis")
    void testGraphEngineCompatibilityWithImpactAnalysis() {
        // Step 1: Create Project
        ProjectResponse project = projectService.createProject(
                new ProjectRequest("FinTech Ecosystem", "Banking microservices"));

        // Step 2: Create Services: WebFrontend, OrderService, PaymentGateway, DatabaseService
        ServiceResponse webFrontend = serviceService.createService(
                new ServiceRequest(project.getId(), "WebFrontend", "Client-facing UI"));
        ServiceResponse orderService = serviceService.createService(
                new ServiceRequest(project.getId(), "OrderService", "Handles order workflows"));
        ServiceResponse paymentGateway = serviceService.createService(
                new ServiceRequest(project.getId(), "PaymentGateway", "Processes charges"));
        ServiceResponse databaseService = serviceService.createService(
                new ServiceRequest(project.getId(), "DatabaseService", "Core database cluster"));

        // Step 3: Create Dependencies following A -> B (A depends on B)
        // WebFrontend -> OrderService
        dependencyService.createDependency(new DependencyRequest(
                webFrontend.getId(), orderService.getId(), "HTTP", "Web calls orders"));

        // OrderService -> PaymentGateway
        dependencyService.createDependency(new DependencyRequest(
                orderService.getId(), paymentGateway.getId(), "REST", "Order calls payment"));

        // PaymentGateway -> DatabaseService
        dependencyService.createDependency(new DependencyRequest(
                paymentGateway.getId(), databaseService.getId(), "JDBC", "Payment accesses database"));

        // Step 4: Build graph using Arjun's data model adapter
        DependencyGraph graph = dependencyService.buildDependencyGraphForProject(project.getId());

        assertNotNull(graph);
        assertEquals(4, graph.getServiceCount());
        assertEquals(3, graph.getEdgeCount());

        // Verify Direction Convention: WebFrontend depends on OrderService
        assertTrue(graph.hasEdge("WebFrontend", "OrderService"));
        assertTrue(graph.hasEdge("OrderService", "PaymentGateway"));
        assertTrue(graph.hasEdge("PaymentGateway", "DatabaseService"));

        // Step 5: Atharv's CycleDetector should confirm no cycles exist
        CycleDetectionResult cycleResult = cycleDetector.detectCycles(graph);
        assertFalse(cycleResult.hasCycle(), "FinTech ecosystem pipeline should be acyclic");

        // Step 6: Atharv's ImpactAnalyzer should correctly determine reverse ripple effects
        // If DatabaseService changes, PaymentGateway, OrderService, and WebFrontend are all impacted!
        ImpactAnalysisResult impactOnDb = impactAnalyzer.analyzeImpact(graph, "DatabaseService");

        assertNotNull(impactOnDb);
        assertEquals("DatabaseService", impactOnDb.changedServiceId());
        assertEquals(3, impactOnDb.getAffectedCount());
        assertEquals(Set.of("PaymentGateway", "OrderService", "WebFrontend"), impactOnDb.affectedServiceIds());

        // Step 7: If WebFrontend changes, it is a leaf (no upstream callers depend on WebFrontend)
        ImpactAnalysisResult impactOnFrontend = impactAnalyzer.analyzeImpact(graph, "WebFrontend");
        assertEquals(0, impactOnFrontend.getAffectedCount());
        assertFalse(impactOnFrontend.hasImpact());
    }

    @Test
    @DisplayName("Should accurately detect cycles using CycleDetector on database-persisted dependencies")
    void testCycleDetectionOnDataModel() {
        ProjectResponse project = projectService.createProject(
                new ProjectRequest("Circular System", "Testing circular references"));

        ServiceResponse svc1 = serviceService.createService(
                new ServiceRequest(project.getId(), "Service1", "Node 1"));
        ServiceResponse svc2 = serviceService.createService(
                new ServiceRequest(project.getId(), "Service2", "Node 2"));

        // Service1 -> Service2
        dependencyService.createDependency(new DependencyRequest(
                svc1.getId(), svc2.getId(), "REST", "1 depends on 2"));

        // Service2 -> Service1 (creates a cycle!)
        dependencyService.createDependency(new DependencyRequest(
                svc2.getId(), svc1.getId(), "REST", "2 depends on 1"));

        DependencyGraph graph = dependencyService.buildDependencyGraphForProject(project.getId());
        assertEquals(2, graph.getServiceCount());
        assertEquals(2, graph.getEdgeCount());

        CycleDetectionResult cycleResult = cycleDetector.detectCycles(graph);
        assertTrue(cycleResult.hasCycle(), "Cycle should be detected between Service1 and Service2");
        assertFalse(cycleResult.cycles().isEmpty());
    }
}
