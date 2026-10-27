package com.impactgraph.controller;

import com.impactgraph.dto.request.DependencyRequest;
import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.request.ServiceRequest;
import com.impactgraph.dto.response.DependencyResponse;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.exception.GlobalExceptionHandler;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@DisplayName("Dependency REST Controller & Data Integrity Tests")
class DependencyControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private DependencyService dependencyService;

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    private Long project1Id;
    private Long project2Id;
    private Long serviceAId;
    private Long serviceBId;
    private Long serviceCId;
    private Long serviceOtherProjectId;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        serviceRepository.deleteAll();
        projectRepository.deleteAll();

        DependencyController controller = new DependencyController(dependencyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Project 1 with Services A, B, C
        ProjectResponse p1 = projectService.createProject(new ProjectRequest("Project 1", "First system"));
        project1Id = p1.getId();

        ServiceResponse sA = serviceService.createService(new ServiceRequest(project1Id, "ServiceA", "Source service"));
        serviceAId = sA.getId();

        ServiceResponse sB = serviceService.createService(new ServiceRequest(project1Id, "ServiceB", "Target service"));
        serviceBId = sB.getId();

        ServiceResponse sC = serviceService.createService(new ServiceRequest(project1Id, "ServiceC", "Third service"));
        serviceCId = sC.getId();

        // Project 2 with Service in another project
        ProjectResponse p2 = projectService.createProject(new ProjectRequest("Project 2", "Second system"));
        project2Id = p2.getId();

        ServiceResponse sOther = serviceService.createService(new ServiceRequest(project2Id, "OtherService", "Different project"));
        serviceOtherProjectId = sOther.getId();
    }

    @Test
    @DisplayName("POST /api/dependencies - Should successfully create dependency respecting A -> B convention")
    void testCreateDependencySuccess() throws Exception {
        // Convention: ServiceA -> ServiceB means ServiceA depends on ServiceB
        String json = """
                {
                    "sourceServiceId": %d,
                    "targetServiceId": %d,
                    "dependencyType": "REST",
                    "description": "ServiceA calls ServiceB via REST"
                }
                """.formatted(serviceAId, serviceBId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.projectId").value(project1Id))
                .andExpect(jsonPath("$.sourceServiceId").value(serviceAId))
                .andExpect(jsonPath("$.sourceServiceName").value("ServiceA"))
                .andExpect(jsonPath("$.targetServiceId").value(serviceBId))
                .andExpect(jsonPath("$.targetServiceName").value("ServiceB"))
                .andExpect(jsonPath("$.dependencyType").value("REST"))
                .andExpect(jsonPath("$.direction").value("ServiceA -> ServiceB"));
    }

    @Test
    @DisplayName("POST /api/dependencies - Should reject self-dependency where source == target")
    void testRejectSelfDependency() throws Exception {
        String json = """
                {
                    "sourceServiceId": %d,
                    "targetServiceId": %d,
                    "dependencyType": "INTERNAL",
                    "description": "Self loop"
                }
                """.formatted(serviceAId, serviceAId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Self-dependency is not allowed")));
    }

    @Test
    @DisplayName("POST /api/dependencies - Should reject duplicate dependencies for the same source-target pair")
    void testRejectDuplicateDependency() throws Exception {
        dependencyService.createDependency(new DependencyRequest(serviceAId, serviceBId, "REST", "Initial"));

        String duplicateJson = """
                {
                    "sourceServiceId": %d,
                    "targetServiceId": %d,
                    "dependencyType": "gRPC",
                    "description": "Duplicate edge"
                }
                """.formatted(serviceAId, serviceBId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/dependencies - Should reject dependencies between services in different projects")
    void testRejectCrossProjectDependency() throws Exception {
        String crossProjectJson = """
                {
                    "sourceServiceId": %d,
                    "targetServiceId": %d,
                    "dependencyType": "REST",
                    "description": "Cross-project call"
                }
                """.formatted(serviceAId, serviceOtherProjectId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossProjectJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("different projects are not allowed")));
    }

    @Test
    @DisplayName("POST /api/dependencies - Should reject missing sourceServiceId or targetServiceId")
    void testRejectMissingRequiredFields() throws Exception {
        String missingSource = """
                {
                    "targetServiceId": %d,
                    "dependencyType": "REST"
                }
                """.formatted(serviceBId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingSource))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sourceServiceId").isNotEmpty());

        String missingTarget = """
                {
                    "sourceServiceId": %d,
                    "dependencyType": "REST"
                }
                """.formatted(serviceAId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingTarget))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetServiceId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/dependencies - Should return 404 when source or target service does not exist")
    void testRejectNonexistentServiceIds() throws Exception {
        String nonexistentSource = """
                {
                    "sourceServiceId": 9999,
                    "targetServiceId": %d
                }
                """.formatted(serviceBId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonexistentSource))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Source service with ID 9999 not found")));

        String nonexistentTarget = """
                {
                    "sourceServiceId": %d,
                    "targetServiceId": 9999
                }
                """.formatted(serviceAId);

        mockMvc.perform(post("/api/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonexistentTarget))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Target service with ID 9999 not found")));
    }

    @Test
    @DisplayName("GET /api/dependencies - Should list dependencies and filter by projectId")
    void testGetAllDependenciesAndFilter() throws Exception {
        dependencyService.createDependency(new DependencyRequest(serviceAId, serviceBId, "REST", "A -> B"));
        dependencyService.createDependency(new DependencyRequest(serviceBId, serviceCId, "ASYNC", "B -> C"));

        mockMvc.perform(get("/api/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/dependencies").param("projectId", project1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/dependencies").param("sourceServiceId", serviceAId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sourceServiceName").value("ServiceA"));
    }

    @Test
    @DisplayName("GET /api/dependencies/{id} - Should return dependency or 404")
    void testGetDependencyById() throws Exception {
        DependencyResponse created = dependencyService.createDependency(
                new DependencyRequest(serviceAId, serviceBId, "REST", "A -> B"));

        mockMvc.perform(get("/api/dependencies/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.direction").value("ServiceA -> ServiceB"));

        mockMvc.perform(get("/api/dependencies/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/dependencies/{id} - Should update dependency or return 404")
    void testUpdateDependency() throws Exception {
        DependencyResponse created = dependencyService.createDependency(
                new DependencyRequest(serviceAId, serviceBId, "REST", "Old desc"));

        String updateJson = """
                {
                    "sourceServiceId": %d,
                    "targetServiceId": %d,
                    "dependencyType": "gRPC",
                    "description": "Updated to gRPC"
                }
                """.formatted(serviceAId, serviceBId);

        mockMvc.perform(put("/api/dependencies/" + created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencyType").value("gRPC"))
                .andExpect(jsonPath("$.description").value("Updated to gRPC"));

        mockMvc.perform(put("/api/dependencies/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/dependencies/{id} - Should delete dependency or return 404")
    void testDeleteDependency() throws Exception {
        DependencyResponse created = dependencyService.createDependency(
                new DependencyRequest(serviceAId, serviceBId, "REST", "A -> B"));

        mockMvc.perform(delete("/api/dependencies/" + created.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dependencies/" + created.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/dependencies/9999"))
                .andExpect(status().isNotFound());
    }
}
