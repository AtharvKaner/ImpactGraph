package com.impactgraph.controller;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DisplayName("Persisted Project Change-Impact Analysis Integration Tests")
class PersistedImpactAnalysisTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

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

    private ProjectResponse project;
    private ServiceResponse orderService;
    private ServiceResponse paymentService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        dependencyRepository.deleteAll();
        serviceRepository.deleteAll();
        projectRepository.deleteAll();

        // Create Project 1
        project = projectService.createProject(
                new ProjectRequest("ImpactGraph Production", "Production microservices topology")
        );

        // Order Service (ID 1) and Payment Service (ID 2)
        orderService = serviceService.createService(
                new ServiceRequest(project.getId(), "Order Service", "Order management and checkout")
        );
        paymentService = serviceService.createService(
                new ServiceRequest(project.getId(), "Payment Service", "Payment processing gateway")
        );

        // Order Service depends on Payment Service (Order Service -> Payment Service)
        dependencyService.createDependency(new DependencyRequest(
                orderService.getId(), paymentService.getId(), "REST", "Order calls payment"
        ));
    }

    @Test
    @DisplayName("Existing demo mode remains unchanged when projectId is absent")
    void testExistingDemoModeRemainsUnchanged() throws Exception {
        // Default preset (ecommerce-dag)
        mockMvc.perform(get("/api/impact/Payment Service")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(3))
                .andExpect(jsonPath("$.affectedServiceIds", containsInAnyOrder("Checkout Service", "Order Service", "Storefront Web")))
                .andExpect(jsonPath("$.cycleResult.hasCycle").value(false));

        // Circular demo preset
        mockMvc.perform(get("/api/impact/Payment Service?preset=circular-demo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(4))
                .andExpect(jsonPath("$.cycleResult.hasCycle").value(true));
    }

    @Test
    @DisplayName("Persisted mode: Analyzing Payment Service identifies Order Service as affected")
    void testPersistedImpact_paymentServiceAffectsOrderService() throws Exception {
        mockMvc.perform(get("/api/impact/Payment Service?projectId=" + project.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.affectedServiceIds", contains("Order Service")))
                .andExpect(jsonPath("$.directAffectedServices", contains("Order Service")))
                .andExpect(jsonPath("$.transitiveAffectedServices", empty()))
                .andExpect(jsonPath("$.affectedServiceIds", not(hasItem("Payment Service"))))
                .andExpect(jsonPath("$.impactPaths", hasSize(1)))
                .andExpect(jsonPath("$.impactPaths[0].affectedServiceId").value("Order Service"))
                .andExpect(jsonPath("$.impactPaths[0].changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.impactPaths[0].path", contains("Order Service", "Payment Service")))
                .andExpect(jsonPath("$.cycleResult.hasCycle").value(false));
    }

    @Test
    @DisplayName("Persisted mode: Querying by numeric service ID resolves service name and computes blast radius")
    void testPersistedImpact_byNumericServiceId() throws Exception {
        mockMvc.perform(get("/api/impact/" + paymentService.getId() + "?projectId=" + project.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Payment Service"))
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.affectedServiceIds", contains("Order Service")));
    }

    @Test
    @DisplayName("Persisted mode: Analyzing Order Service does not identify Payment Service as affected")
    void testPersistedImpact_orderServiceDoesNotAffectPaymentService() throws Exception {
        mockMvc.perform(get("/api/impact/Order Service?projectId=" + project.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedServiceId").value("Order Service"))
                .andExpect(jsonPath("$.affectedCount").value(0))
                .andExpect(jsonPath("$.affectedServiceIds", empty()))
                .andExpect(jsonPath("$.affectedServiceIds", not(hasItem("Payment Service"))))
                .andExpect(jsonPath("$.directAffectedServices", empty()))
                .andExpect(jsonPath("$.transitiveAffectedServices", empty()))
                .andExpect(jsonPath("$.impactPaths", empty()));
    }

    @Test
    @DisplayName("Persisted mode: Unknown service name returns 404 Not Found")
    void testPersistedImpact_unknownService_returns404() throws Exception {
        mockMvc.perform(get("/api/impact/NonExistentService?projectId=" + project.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("NonExistentService")));
    }

    @Test
    @DisplayName("Persisted mode: Nonexistent project ID returns 404 Not Found and does not fall back to demo")
    void testPersistedImpact_missingProject_returns404() throws Exception {
        mockMvc.perform(get("/api/impact/Payment Service?projectId=99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("Project with ID 99999 not found")));
    }
}
