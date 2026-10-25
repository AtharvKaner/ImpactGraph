package com.impactgraph.controller;

import com.impactgraph.dto.request.ProjectRequest;
import com.impactgraph.exception.GlobalExceptionHandler;
import com.impactgraph.repository.DependencyRepository;
import com.impactgraph.repository.ProjectRepository;
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
@DisplayName("Project REST Controller & Data Integrity Tests")
class ProjectControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private DependencyService dependencyService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DependencyRepository dependencyRepository;

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        projectRepository.deleteAll();

        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("POST /api/projects - Should successfully create a new project")
    void testCreateProjectSuccess() throws Exception {
        String json = """
                {
                    "name": "E-Commerce System",
                    "description": "Microservices architecture for retail"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("E-Commerce System"))
                .andExpect(jsonPath("$.description").value("Microservices architecture for retail"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.serviceCount").value(0));
    }

    @Test
    @DisplayName("POST /api/projects - Should reject blank or missing project name")
    void testCreateProjectValidationFailure() throws Exception {
        String json = """
                {
                    "name": "   ",
                    "description": "Description without name"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/projects - Should reject duplicate project names with 409 Conflict")
    void testCreateDuplicateProjectFailure() throws Exception {
        String json1 = """
                {
                    "name": "Banking Core",
                    "description": "Initial banking core"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json1))
                .andExpect(status().isCreated());

        String json2 = """
                {
                    "name": "Banking Core",
                    "description": "Duplicate banking core"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("GET /api/projects - Should return 200 OK and empty array through ApplicationContext when no projects exist")
    void testGetAllProjects_emptyListThroughApplicationContext() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/projects - Should retrieve list of projects")
    void testGetAllProjects() throws Exception {
        projectService.createProject(new ProjectRequest("Project Alpha", "First project"));
        projectService.createProject(new ProjectRequest("Project Beta", "Second project"));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Project Alpha"))
                .andExpect(jsonPath("$[1].name").value("Project Beta"));
    }

    @Test
    @DisplayName("GET /api/projects/{id} - Should return project by ID")
    void testGetProjectByIdSuccess() throws Exception {
        var created = projectService.createProject(new ProjectRequest("Logistics System", "Tracking and fleet"));

        mockMvc.perform(get("/api/projects/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.name").value("Logistics System"));
    }

    @Test
    @DisplayName("GET /api/projects/{id} - Should return 404 for nonexistent project ID")
    void testGetProjectByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/projects/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Project with ID 9999 not found")));
    }

    @Test
    @DisplayName("PUT /api/projects/{id} - Should update project successfully")
    void testUpdateProjectSuccess() throws Exception {
        var created = projectService.createProject(new ProjectRequest("Old Name", "Old Description"));

        String updateJson = """
                {
                    "name": "Updated Name",
                    "description": "Updated Description"
                }
                """;

        mockMvc.perform(put("/api/projects/" + created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.description").value("Updated Description"));
    }

    @Test
    @DisplayName("PUT /api/projects/{id} - Should return 404 when updating nonexistent project")
    void testUpdateProjectNotFound() throws Exception {
        String updateJson = """
                {
                    "name": "Updated Name",
                    "description": "Updated Description"
                }
                """;

        mockMvc.perform(put("/api/projects/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} - Should delete project successfully")
    void testDeleteProjectSuccess() throws Exception {
        var created = projectService.createProject(new ProjectRequest("Temp Project", "To be deleted"));

        mockMvc.perform(delete("/api/projects/" + created.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + created.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} - Should return 404 when deleting nonexistent project")
    void testDeleteProjectNotFound() throws Exception {
        mockMvc.perform(delete("/api/projects/9999"))
                .andExpect(status().isNotFound());
    }
}
