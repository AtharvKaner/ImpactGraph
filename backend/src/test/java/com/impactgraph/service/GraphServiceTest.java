package com.impactgraph.service;

import com.impactgraph.dto.*;
import com.impactgraph.graph.DefaultCycleDetector;
import com.impactgraph.graph.DefaultImpactAnalyzer;
import com.impactgraph.graph.exception.GraphValidationException;
import com.impactgraph.graph.exception.InvalidServiceIdException;
import com.impactgraph.graph.exception.UnknownServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphServiceTest {

    private GraphService graphService;
    private DemonstrationGraphCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DemonstrationGraphCatalog();
        graphService = new GraphService(
                catalog,
                new DefaultImpactAnalyzer(),
                new DefaultCycleDetector()
        );
    }

    @Test
    @DisplayName("Default preset defaults to 'ecommerce-dag' with 8 services")
    void defaultPreset_resolvesToEcommerceDag() {
        List<ServiceDto> defaultServices = graphService.getServices(null);
        List<ServiceDto> emptyServices = graphService.getServices("  ");
        List<ServiceDto> explicitServices = graphService.getServices("ecommerce-dag");

        assertThat(defaultServices).hasSize(8);
        assertThat(emptyServices).isEqualTo(defaultServices);
        assertThat(explicitServices).isEqualTo(defaultServices);
    }

    @Test
    @DisplayName("Services metadata contains all required fields matching frontend dataset")
    void servicesMetadata_matchesFrontendSpecification() {
        List<ServiceDto> services = graphService.getServices("ecommerce-dag");

        assertThat(services).hasSize(8);
        assertThat(services).extracting(ServiceDto::id).containsExactly(
                "Storefront Web",
                "Checkout Service",
                "Auth Service",
                "Order Service",
                "Payment Service",
                "Inventory Service",
                "Notification Service",
                "Audit Logger"
        );

        ServiceDto paymentService = services.stream()
                .filter(s -> s.id().equals("Payment Service"))
                .findFirst()
                .orElseThrow();
        assertThat(paymentService.label()).isEqualTo("Payment Service");
        assertThat(paymentService.type()).isEqualTo("Core Domain");
        assertThat(paymentService.tier()).isEqualTo("Domain Tier");
        assertThat(paymentService.icon()).isEqualTo("card");
        assertThat(paymentService.description()).isNotEmpty();
    }

    @Test
    @DisplayName("Acyclic DAG preset returns 8 services, 10 edges, and no cycles")
    void getGraph_ecommerceDag_returnsAcyclicTopology() {
        GraphResponseDto graph = graphService.getGraph("ecommerce-dag");

        assertThat(graph.preset()).isEqualTo("ecommerce-dag");
        assertThat(graph.serviceCount()).isEqualTo(8);
        assertThat(graph.edgeCount()).isEqualTo(10);
        assertThat(graph.hasCycle()).isFalse();
        assertThat(graph.cycles()).isEmpty();
        assertThat(graph.services()).hasSize(8);
        assertThat(graph.dependencies()).hasSize(10);
    }

    @Test
    @DisplayName("Circular demo preset returns 8 services, 11 edges, and 1 cycle")
    void getGraph_circularDemo_returnsCircularTopology() {
        GraphResponseDto graph = graphService.getGraph("circular-demo");

        assertThat(graph.preset()).isEqualTo("circular-demo");
        assertThat(graph.serviceCount()).isEqualTo(8);
        assertThat(graph.edgeCount()).isEqualTo(11);
        assertThat(graph.hasCycle()).isTrue();
        assertThat(graph.cycles()).hasSize(1);

        CycleDto cycle = graph.cycles().getFirst();
        assertThat(cycle.nodes()).containsExactly(
                "Payment Service",
                "Notification Service",
                "Payment Service"
        );
        assertThat(cycle.length()).isEqualTo(2);
        assertThat(cycle.cycleString()).isEqualTo(
                "Payment Service -> Notification Service -> Payment Service"
        );
    }

    @Test
    @DisplayName("Dependency edge format preserves direction: source is dependent, target is dependency")
    void edgeDirection_preservesDependencySemantics() {
        GraphResponseDto graph = graphService.getGraph("ecommerce-dag");

        DependencyEdgeDto storefrontToCheckout = graph.dependencies().stream()
                .filter(e -> e.source().equals("Storefront Web") && e.target().equals("Checkout Service"))
                .findFirst()
                .orElseThrow();

        assertThat(storefrontToCheckout.dependentId()).isEqualTo("Storefront Web");
        assertThat(storefrontToCheckout.dependencyId()).isEqualTo("Checkout Service");

        // Verify that reverse edge does not exist
        boolean reverseExists = graph.dependencies().stream()
                .anyMatch(e -> e.source().equals("Checkout Service") && e.target().equals("Storefront Web"));
        assertThat(reverseExists).isFalse();
    }

    @Test
    @DisplayName("Change impact analysis for Payment Service in ecommerce-dag returns blast radius of 3")
    void analyzeImpact_paymentService_calculatesCorrectBlastRadius() {
        ImpactAnalysisResponseDto impact = graphService.analyzeImpact("ecommerce-dag", "Payment Service");

        assertThat(impact.changedServiceId()).isEqualTo("Payment Service");
        assertThat(impact.affectedCount()).isEqualTo(3);
        assertThat(impact.affectedServiceIds()).containsExactlyInAnyOrder(
                "Checkout Service",
                "Order Service",
                "Storefront Web"
        );
        assertThat(impact.directAffectedServices()).containsExactlyInAnyOrder(
                "Checkout Service",
                "Order Service"
        );
        assertThat(impact.transitiveAffectedServices()).containsExactly(
                "Storefront Web"
        );
        assertThat(impact.cycleResult().hasCycle()).isFalse();

        // Check explanatory paths
        List<ImpactPathDto> paths = impact.impactPaths();
        assertThat(paths).hasSize(3);

        ImpactPathDto checkoutPath = paths.stream()
                .filter(p -> p.affectedServiceId().equals("Checkout Service"))
                .findFirst()
                .orElseThrow();
        assertThat(checkoutPath.distance()).isEqualTo(1);
        assertThat(checkoutPath.isDirect()).isTrue();
        assertThat(checkoutPath.isTransitive()).isFalse();
        assertThat(checkoutPath.path()).containsExactly("Checkout Service", "Payment Service");
        assertThat(checkoutPath.pathString()).isEqualTo("Checkout Service -> Payment Service");

        ImpactPathDto orderPath = paths.stream()
                .filter(p -> p.affectedServiceId().equals("Order Service"))
                .findFirst()
                .orElseThrow();
        assertThat(orderPath.distance()).isEqualTo(1);
        assertThat(orderPath.isDirect()).isTrue();
        assertThat(orderPath.isTransitive()).isFalse();
        assertThat(orderPath.path()).containsExactly("Order Service", "Payment Service");

        ImpactPathDto storefrontPath = paths.stream()
                .filter(p -> p.affectedServiceId().equals("Storefront Web"))
                .findFirst()
                .orElseThrow();
        assertThat(storefrontPath.distance()).isEqualTo(2);
        assertThat(storefrontPath.isDirect()).isFalse();
        assertThat(storefrontPath.isTransitive()).isTrue();
        assertThat(storefrontPath.path()).containsExactly("Storefront Web", "Checkout Service", "Payment Service");
        assertThat(storefrontPath.pathString()).isEqualTo("Storefront Web -> Checkout Service -> Payment Service");
    }

    @Test
    @DisplayName("Change impact analysis for Payment Service in circular-demo returns blast radius of 4")
    void analyzeImpact_circularDemo_handlesCyclesSafely() {
        ImpactAnalysisResponseDto impact = graphService.analyzeImpact("circular-demo", "Payment Service");

        assertThat(impact.changedServiceId()).isEqualTo("Payment Service");
        assertThat(impact.affectedCount()).isEqualTo(4);
        assertThat(impact.affectedServiceIds()).containsExactlyInAnyOrder(
                "Checkout Service",
                "Notification Service",
                "Order Service",
                "Storefront Web"
        );
        assertThat(impact.directAffectedServices()).containsExactlyInAnyOrder(
                "Checkout Service",
                "Notification Service",
                "Order Service"
        );
        assertThat(impact.transitiveAffectedServices()).containsExactly(
                "Storefront Web"
        );

        // Crucial correctness invariant: changed target is NEVER counted as affected,
        // even when a cycle loops back to it
        assertThat(impact.affectedServiceIds()).doesNotContain("Payment Service");
        assertThat(impact.directAffectedServices()).doesNotContain("Payment Service");
        assertThat(impact.transitiveAffectedServices()).doesNotContain("Payment Service");

        assertThat(impact.cycleResult().hasCycle()).isTrue();
        assertThat(impact.cycleResult().cycleCount()).isEqualTo(1);

        // Check explanatory paths
        List<ImpactPathDto> paths = impact.impactPaths();
        assertThat(paths).hasSize(4);

        ImpactPathDto notifPath = paths.stream()
                .filter(p -> p.affectedServiceId().equals("Notification Service"))
                .findFirst()
                .orElseThrow();
        assertThat(notifPath.distance()).isEqualTo(1);
        assertThat(notifPath.isDirect()).isTrue();
        assertThat(notifPath.path()).containsExactly("Notification Service", "Payment Service");
        assertThat(notifPath.pathString()).isEqualTo("Notification Service -> Payment Service");

        ImpactPathDto storefrontPath = paths.stream()
                .filter(p -> p.affectedServiceId().equals("Storefront Web"))
                .findFirst()
                .orElseThrow();
        assertThat(storefrontPath.distance()).isEqualTo(2);
        assertThat(storefrontPath.isTransitive()).isTrue();
    }

    @Test
    @DisplayName("Impact analysis on unknown service throws UnknownServiceException")
    void analyzeImpact_unknownService_throwsUnknownServiceException() {
        assertThatThrownBy(() -> graphService.analyzeImpact("ecommerce-dag", "NonExistentService"))
                .isInstanceOf(UnknownServiceException.class)
                .hasMessageContaining("NonExistentService");
    }

    @Test
    @DisplayName("Impact analysis on blank or null service ID throws InvalidServiceIdException")
    void analyzeImpact_blankServiceId_throwsInvalidServiceIdException() {
        assertThatThrownBy(() -> graphService.analyzeImpact("ecommerce-dag", null))
                .isInstanceOf(InvalidServiceIdException.class);

        assertThatThrownBy(() -> graphService.analyzeImpact("ecommerce-dag", "   "))
                .isInstanceOf(InvalidServiceIdException.class);
    }

    @Test
    @DisplayName("Invalid preset throws GraphValidationException")
    void invalidPreset_throwsGraphValidationException() {
        assertThatThrownBy(() -> graphService.getServices("non-existent-preset"))
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("non-existent-preset");

        assertThatThrownBy(() -> graphService.getGraph("invalid-graph"))
                .isInstanceOf(GraphValidationException.class);

        assertThatThrownBy(() -> graphService.detectCycles("bad-preset"))
                .isInstanceOf(GraphValidationException.class);
    }

    @Test
    @DisplayName("detectCycles returns clean results for both presets")
    void detectCycles_validatesBothPresets() {
        CycleDetectionResponseDto dagCycles = graphService.detectCycles("ecommerce-dag");
        assertThat(dagCycles.hasCycle()).isFalse();
        assertThat(dagCycles.cycleCount()).isEqualTo(0);
        assertThat(dagCycles.cycles()).isEmpty();

        CycleDetectionResponseDto circularCycles = graphService.detectCycles("circular-demo");
        assertThat(circularCycles.hasCycle()).isTrue();
        assertThat(circularCycles.cycleCount()).isEqualTo(1);
        assertThat(circularCycles.cycles()).hasSize(1);
    }

    @Test
    @DisplayName("analyzeImpact with projectId delegates to DependencyService and resolves service")
    void analyzeImpact_withProjectId_delegatesToDependencyService() {
        DependencyService mockDependencyService = org.mockito.Mockito.mock(DependencyService.class);
        com.impactgraph.graph.DependencyGraph mockGraph = com.impactgraph.graph.DependencyGraph.builder()
                .addService("Order Service")
                .addService("Payment Service")
                .addDependency("Order Service", "Payment Service")
                .build();

        org.mockito.Mockito.when(mockDependencyService.buildDependencyGraphForProject(42L))
                .thenReturn(mockGraph);
        org.mockito.Mockito.when(mockDependencyService.resolveServiceName(42L, "2"))
                .thenReturn("Payment Service");

        GraphService serviceWithDep = new GraphService(
                catalog,
                new DefaultImpactAnalyzer(),
                new DefaultCycleDetector(),
                mockDependencyService
        );

        ImpactAnalysisResponseDto response = serviceWithDep.analyzeImpact(null, "2", 42L);

        assertThat(response.changedServiceId()).isEqualTo("Payment Service");
        assertThat(response.affectedCount()).isEqualTo(1);
        assertThat(response.affectedServiceIds()).containsExactly("Order Service");
        org.mockito.Mockito.verify(mockDependencyService).buildDependencyGraphForProject(42L);
        org.mockito.Mockito.verify(mockDependencyService).resolveServiceName(42L, "2");
    }

    @Test
    @DisplayName("analyzeImpact with projectId throws IllegalStateException if DependencyService is null")
    void analyzeImpact_withProjectId_whenDependencyServiceNull_throwsIllegalStateException() {
        assertThatThrownBy(() -> graphService.analyzeImpact(null, "Payment Service", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DependencyService is not configured");
    }
}
