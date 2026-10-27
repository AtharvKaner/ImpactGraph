package com.impactgraph.controller;

import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.request.ServiceRequest;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.exception.GlobalExceptionHandler;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.ServiceRepository;
import com.impactgraph.service.ApiService;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DisplayName("Service REST Controller & Data Integrity Tests")
class ServiceControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private ApiService apiService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    private Long testProjectId;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        serviceRepository.deleteAll();
        projectRepository.deleteAll();

        ServiceController controller = new ServiceController(serviceService, apiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ProjectResponse project = projectService.createProject(
                new ProjectRequest("Parent Project", "For service testing"));
        testProjectId = project.getId();
    }

    @Test
    @DisplayName("POST /api/services - Should successfully create service under existing project")
    void testCreateServiceSuccess() throws Exception {
        String json = """
                {
                    "projectId": %d,
                    "name": "OrderService",
                    "description": "Handles order placements"
                }
                """.formatted(testProjectId);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.projectId").value(testProjectId))
                .andExpect(jsonPath("$.projectName").value("Parent Project"))
                .andExpect(jsonPath("$.name").value("OrderService"))
                .andExpect(jsonPath("$.description").value("Handles order placements"))
                .andExpect(jsonPath("$.apiCount").value(0));
    }

    @Test
    @DisplayName("POST /api/services - Should return 404 when referencing nonexistent project")
    void testCreateServiceNonexistentProject() throws Exception {
        String json = """
                {
                    "projectId": 9999,
                    "name": "OrphanService",
                    "description": "Has no valid project"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Project with ID 9999 not found")));
    }

    @Test
    @DisplayName("POST /api/services - Should reject missing name or missing projectId with 400 Bad Request")
    void testCreateServiceValidationFailures() throws Exception {
        // Missing name
        String missingNameJson = """
                {
                    "projectId": %d,
                    "name": "   ",
                    "description": "Desc"
                }
                """.formatted(testProjectId);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingNameJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());

        // Missing projectId
        String missingProjectJson = """
                {
                    "name": "ValidName",
                    "description": "Desc"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingProjectJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.projectId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/services - Should reject duplicate service names within the same project")
    void testCreateDuplicateServiceInSameProject() throws Exception {
        serviceService.createService(new ServiceRequest(testProjectId, "AuthService", "First"));

        String duplicateJson = """
                {
                    "projectId": %d,
                    "name": "AuthService",
                    "description": "Duplicate"
                }
                """.formatted(testProjectId);

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists in project")));
    }

    @Test
    @DisplayName("GET /api/services - Should retrieve all services or filter by projectId")
    void testGetAllServicesAndFilter() throws Exception {
        serviceService.createService(new ServiceRequest(testProjectId, "ServiceA", "A"));
        serviceService.createService(new ServiceRequest(testProjectId, "ServiceB", "B"));

        // Unfiltered
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Filtered by existing project
        mockMvc.perform(get("/api/services").param("projectId", testProjectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Filtered by nonexistent project -> 404
        mockMvc.perform(get("/api/services").param("projectId", "8888"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/services/{id} - Should return service or 404 if missing")
    void testGetServiceById() throws Exception {
        ServiceResponse created = serviceService.createService(
                new ServiceRequest(testProjectId, "PaymentService", "Payment gateway"));

        mockMvc.perform(get("/api/services/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.name").value("PaymentService"));

        mockMvc.perform(get("/api/services/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/services/{id} - Should update service or return 404")
    void testUpdateService() throws Exception {
        ServiceResponse created = serviceService.createService(
                new ServiceRequest(testProjectId, "InitialService", "Initial"));

        String updateJson = """
                {
                    "projectId": %d,
                    "name": "RenamedService",
                    "description": "Updated description"
                }
                """.formatted(testProjectId);

        mockMvc.perform(put("/api/services/" + created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("RenamedService"))
                .andExpect(jsonPath("$.description").value("Updated description"));

        // 404 on nonexistent ID
        mockMvc.perform(put("/api/services/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/services/{id} - Should delete service or return 404")
    void testDeleteService() throws Exception {
        ServiceResponse created = serviceService.createService(
                new ServiceRequest(testProjectId, "EphemeralService", "Soon gone"));

        mockMvc.perform(delete("/api/services/" + created.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/services/" + created.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/services/9999"))
                .andExpect(status().isNotFound());
    }
}
