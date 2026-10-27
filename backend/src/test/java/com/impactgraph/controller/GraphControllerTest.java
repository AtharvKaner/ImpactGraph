package com.impactgraph.controller;

import com.impactgraph.controller.exception.GraphExceptionHandler;
import com.impactgraph.graph.DefaultCycleDetector;
import com.impactgraph.graph.DefaultImpactAnalyzer;
import com.impactgraph.service.DemonstrationGraphCatalog;
import com.impactgraph.service.GraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GraphControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DemonstrationGraphCatalog catalog = new DemonstrationGraphCatalog();
        GraphService graphService = new GraphService(
                catalog,
                new DefaultImpactAnalyzer(),
                new DefaultCycleDetector()
        );
        GraphController graphController = new GraphController(graphService);
        HealthController healthController = new HealthController();

        mockMvc = MockMvcBuilders.standaloneSetup(graphController, healthController)
                .setControllerAdvice(new GraphExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/health remains functional returning UP status")
    void healthCheckRemainsFunctional() throws Exception {
        mockMvc.perform(get("/api/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ImpactGraph Backend"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/services with default preset returns 8 services with metadata")
    void getServices_defaultPreset_returnsCatalog() throws Exception {
        mockMvc.perform(get("/api/services")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].id").value("Storefront Web"))
                .andExpect(jsonPath("$[0].label").value("Storefront Web"))
                .andExpect(jsonPath("$[0].tier").value("Client Tier"))
                .andExpect(jsonPath("$[0].type").value("Frontend / Gateway"))
                .andExpect(jsonPath("$[0].icon").value("globe"))
                .andExpect(jsonPath("$[0].description").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/services with explicit presets works for both ecommerce-dag and circular-demo")
    void getServices_explicitPresets_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/services?preset=ecommerce-dag")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        mockMvc.perform(get("/api/services?preset=circular-demo")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    @DisplayName("GET /api/services with invalid preset returns 400 Bad Request")
    void getServices_invalidPreset_returns400() throws Exception {
        mockMvc.perform(get("/api/services?preset=unknown-preset")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("Unknown preset")));
    }

    @Test
    @DisplayName("GET /api/graph with default preset returns 8 services, 10 edges, no cycles")
    void getGraph_defaultPreset_returnsAcyclicGraph() throws Exception {
        mockMvc.perform(get("/api/graph")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("ecommerce-dag"))
                .andExpect(jsonPath("$.serviceCount").value(8))
                .andExpect(jsonPath("$.edgeCount").value(10))
                .andExpect(jsonPath("$.hasCycle").value(false))
                .andExpect(jsonPath("$.services", hasSize(8)))
                .andExpect(jsonPath("$.dependencies", hasSize(10)))
                .andExpect(jsonPath("$.cycles", empty()));
    }

    @Test
    @DisplayName("GET /api/graph returns React Flow-compatible source and target edge properties")
    void getGraph_edgesExposeSourceAndTarget() throws Exception {
        mockMvc.perform(get("/api/graph?preset=ecommerce-dag")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencies[0].source").isNotEmpty())
                .andExpect(jsonPath("$.dependencies[0].target").isNotEmpty())
                .andExpect(jsonPath("$.dependencies[0].dependentId").isNotEmpty())
                .andExpect(jsonPath("$.dependencies[0].dependencyId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/graph with circular-demo returns 8 services, 11 edges, hasCycle true")
    void getGraph_circularDemo_returnsCircularGraph() throws Exception {
        mockMvc.perform(get("/api/graph?preset=circular-demo")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("circular-demo"))
                .andExpect(jsonPath("$.serviceCount").value(8))
                .andExpect(jsonPath("$.edgeCount").value(11))
                .andExpect(jsonPath("$.hasCycle").value(true))
                .andExpect(jsonPath("$.cycles", hasSize(1)))
                .andExpect(jsonPath("$.cycles[0].length").value(2))
                .andExpect(jsonPath("$.cycles[0].cycleString").value("Payment Service -> Notification Service -> Payment Service"));
    }

    @Test
    @DisplayName("GET /api/impact/{serviceId} calculates blast radius for Payment Service")
    void analyzeImpact_paymentService_returnsBlastRadius() throws Exception {
        mockMvc.perform(get("/api/impact/Payment Service?preset=ecommerce-dag")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(3))
                .andExpect(jsonPath("$.affectedServiceIds", containsInAnyOrder("Checkout Service", "Order Service", "Storefront Web")))
                .andExpect(jsonPath("$.directAffectedServices", containsInAnyOrder("Checkout Service", "Order Service")))
                .andExpect(jsonPath("$.transitiveAffectedServices", containsInAnyOrder("Storefront Web")))
                .andExpect(jsonPath("$.impactPaths", hasSize(3)))
                .andExpect(jsonPath("$.cycleResult.hasCycle").value(false));
    }

    @Test
    @DisplayName("GET /api/impact/{serviceId}?preset=circular-demo returns blast radius of 4 for Payment Service")
    void analyzeImpact_circularDemo_paymentService_returnsBlastRadiusOf4() throws Exception {
        mockMvc.perform(get("/api/impact/Payment Service?preset=circular-demo")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(4))
                .andExpect(jsonPath("$.affectedServiceIds", containsInAnyOrder("Checkout Service", "Notification Service", "Order Service", "Storefront Web")))
                .andExpect(jsonPath("$.directAffectedServices", containsInAnyOrder("Checkout Service", "Notification Service", "Order Service")))
                .andExpect(jsonPath("$.transitiveAffectedServices", containsInAnyOrder("Storefront Web")))
                .andExpect(jsonPath("$.affectedServiceIds", not(hasItem("Payment Service"))))
                .andExpect(jsonPath("$.impactPaths", hasSize(4)))
                .andExpect(jsonPath("$.cycleResult.hasCycle").value(true))
                .andExpect(jsonPath("$.cycleResult.cycleCount").value(1))
                .andExpect(jsonPath("$.cycleResult.cycles[0].cycleString").value("Payment Service -> Notification Service -> Payment Service"));
    }

    @Test
    @DisplayName("GET /api/impact/{serviceId} handles URL-encoded service IDs")
    void analyzeImpact_urlEncodedServiceId_succeeds() throws Exception {
        mockMvc.perform(get("/api/impact/Payment%20Service")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(3));
    }

    @Test
    @DisplayName("GET /api/impact/{serviceId} returns 404 Not Found for unregistered service")
    void analyzeImpact_unknownService_returns404() throws Exception {
        mockMvc.perform(get("/api/impact/NonExistentService")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("NonExistentService")));
    }

    @Test
    @DisplayName("GET /api/impact returns 400 Bad Request when service ID is missing or blank")
    void analyzeImpact_blankService_returns400() throws Exception {
        mockMvc.perform(get("/api/impact")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("cannot be null or blank")));

        mockMvc.perform(get("/api/impact/%20")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("cannot be null or blank")));
    }

    @Test
    @DisplayName("GET /api/cycles returns no cycles for acyclic topology")
    void getCycles_ecommerceDag_returnsNoCycles() throws Exception {
        mockMvc.perform(get("/api/cycles?preset=ecommerce-dag")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCycle").value(false))
                .andExpect(jsonPath("$.cycleCount").value(0))
                .andExpect(jsonPath("$.cycles", empty()));
    }

    @Test
    @DisplayName("GET /api/cycles returns detected cycle for circular-demo")
    void getCycles_circularDemo_returnsCycle() throws Exception {
        mockMvc.perform(get("/api/cycles?preset=circular-demo")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCycle").value(true))
                .andExpect(jsonPath("$.cycleCount").value(1))
                .andExpect(jsonPath("$.cycles[0].cycleString").value("Payment Service -> Notification Service -> Payment Service"));
    }

    @Test
    @DisplayName("GET /api/cycles with invalid preset returns 400 Bad Request")
    void getCycles_invalidPreset_returns400() throws Exception {
        mockMvc.perform(get("/api/cycles?preset=invalid")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Unknown preset")));
    }
}
