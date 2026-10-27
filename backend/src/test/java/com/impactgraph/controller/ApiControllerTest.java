package com.impactgraph.controller;

import com.impactgraph.dto.request.ApiRequest;
import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.dto.request.ServiceRequest;
import com.impactgraph.dto.response.ApiResponse;
import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.dto.response.ServiceResponse;
import com.impactgraph.exception.GlobalExceptionHandler;
import com.impactgraph.repository.ApiRepository;
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
@DisplayName("API REST Controller & Endpoint Integrity Tests")
class ApiControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private ApiService apiService;

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ApiRepository apiRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    private Long testServiceId;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        apiRepository.deleteAll();
        serviceRepository.deleteAll();
        projectRepository.deleteAll();

        ApiController controller = new ApiController(apiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ProjectResponse project = projectService.createProject(
                new ProjectRequest("Web Project", "Project for API testing"));
        ServiceResponse service = serviceService.createService(
                new ServiceRequest(project.getId(), "CatalogService", "Product catalog"));
        testServiceId = service.getId();
    }

    @Test
    @DisplayName("POST /api/apis - Should successfully create API under service")
    void testCreateApiSuccess() throws Exception {
        String json = """
                {
                    "serviceId": %d,
                    "name": "Get Product Details",
                    "method": "GET",
                    "path": "/products/{id}",
                    "description": "Fetches product by primary key"
                }
                """.formatted(testServiceId);

        mockMvc.perform(post("/api/apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.serviceId").value(testServiceId))
                .andExpect(jsonPath("$.serviceName").value("CatalogService"))
                .andExpect(jsonPath("$.name").value("Get Product Details"))
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.path").value("/products/{id}"))
                .andExpect(jsonPath("$.description").value("Fetches product by primary key"));
    }

    @Test
    @DisplayName("POST /api/apis - Should reject invalid HTTP method")
    void testCreateApiInvalidMethod() throws Exception {
        String json = """
                {
                    "serviceId": %d,
                    "name": "Invalid API",
                    "method": "FOOBAR",
                    "path": "/products",
                    "description": "Invalid HTTP method"
                }
                """.formatted(testServiceId);

        mockMvc.perform(post("/api/apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.method").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/apis - Should reject path not starting with '/'")
    void testCreateApiPathMissingLeadingSlash() throws Exception {
        String json = """
                {
                    "serviceId": %d,
                    "name": "Bad Path API",
                    "method": "POST",
                    "path": "products/add",
                    "description": "Missing leading slash"
                }
                """.formatted(testServiceId);

        mockMvc.perform(post("/api/apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.path").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/apis - Should reject duplicate endpoint (method + path) in same service")
    void testCreateDuplicateApiInSameService() throws Exception {
        apiService.createApi(new ApiRequest(testServiceId, "List Products", "GET", "/products", "Initial"));

        String duplicateJson = """
                {
                    "serviceId": %d,
                    "name": "Duplicate List Products",
                    "method": "GET",
                    "path": "/products",
                    "description": "Same method and path"
                }
                """.formatted(testServiceId);

        mockMvc.perform(post("/api/apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists for service")));
    }

    @Test
    @DisplayName("POST /api/apis - Should return 404 when referencing nonexistent service")
    void testCreateApiNonexistentService() throws Exception {
        String json = """
                {
                    "serviceId": 9999,
                    "name": "Orphan API",
                    "method": "GET",
                    "path": "/orphan",
                    "description": "Parent service does not exist"
                }
                """;

        mockMvc.perform(post("/api/apis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Service with ID 9999 not found")));
    }

    @Test
    @DisplayName("GET /api/apis - Should list APIs and support serviceId filter")
    void testGetAllApisAndFilter() throws Exception {
        apiService.createApi(new ApiRequest(testServiceId, "Endpoint 1", "GET", "/ep1", "Desc"));
        apiService.createApi(new ApiRequest(testServiceId, "Endpoint 2", "POST", "/ep2", "Desc"));

        mockMvc.perform(get("/api/apis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/apis").param("serviceId", testServiceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/apis").param("serviceId", "7777"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/apis/{id} - Should return API by ID or 404")
    void testGetApiById() throws Exception {
        ApiResponse created = apiService.createApi(
                new ApiRequest(testServiceId, "Get One", "GET", "/item", "Desc"));

        mockMvc.perform(get("/api/apis/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.name").value("Get One"));

        mockMvc.perform(get("/api/apis/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /api/apis/{id} - Should update API or return 404")
    void testUpdateApi() throws Exception {
        ApiResponse created = apiService.createApi(
                new ApiRequest(testServiceId, "Initial API", "GET", "/init", "Desc"));

        String updateJson = """
                {
                    "serviceId": %d,
                    "name": "Updated API",
                    "method": "PUT",
                    "path": "/updated",
                    "description": "Updated endpoint"
                }
                """.formatted(testServiceId);

        mockMvc.perform(put("/api/apis/" + created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated API"))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.path").value("/updated"));

        mockMvc.perform(put("/api/apis/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/apis/{id} - Should delete API or return 404")
    void testDeleteApi() throws Exception {
        ApiResponse created = apiService.createApi(
                new ApiRequest(testServiceId, "Ephemeral API", "DELETE", "/delete-me", "Desc"));

        mockMvc.perform(delete("/api/apis/" + created.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/apis/" + created.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/apis/9999"))
                .andExpect(status().isNotFound());
    }
}
