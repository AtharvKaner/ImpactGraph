package com.impactgraph.service;

import com.impactgraph.dto.response.ProjectResponse;
import com.impactgraph.entity.Project;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.security.JwtAuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "demo.seeder.enabled=true")
@Transactional
class DemoProjectTest {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    @DisplayName("DemoDataSeeder seeds deterministic demo project idempotently")
    void seedDemoProject_createsExpectedTopologyAndIsIdempotent() {
        Project seeded = demoDataSeeder.seedDemoProjectIfMissing();
        assertThat(seeded).isNotNull();
        assertThat(seeded.getName()).isEqualTo(DemoDataSeeder.DEMO_PROJECT_NAME);

        // Calling a second time returns the existing project without duplicate creation
        Project secondRun = demoDataSeeder.seedDemoProjectIfMissing();
        assertThat(secondRun.getId()).isEqualTo(seeded.getId());
        long demoCount = projectRepository.findAll().stream()
                .filter(p -> DemoDataSeeder.DEMO_PROJECT_NAME.equals(p.getName()))
                .count();
        assertThat(demoCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Demo project is accessible to DEVELOPER users without requiring membership")
    void demoProject_isAccessibleToAllUsers() {
        Project seeded = demoDataSeeder.seedDemoProjectIfMissing();

        JwtAuthenticatedUser developerUser = new JwtAuthenticatedUser(
                UUID.randomUUID(),
                "some-dev@impactgraph.io",
                "DEVELOPER"
        );

        // canAccessProject must return true
        boolean canAccess = projectService.canAccessProject(developerUser, seeded.getId());
        assertThat(canAccess).isTrue();

        // getAllProjects must include the demo project even if the user has no memberships
        List<ProjectResponse> all = projectService.getAllProjects(developerUser);
        assertThat(all).anyMatch(p -> p.getId().equals(seeded.getId()) && p.getName().contains("Demo"));
    }

    @Autowired
    private com.impactgraph.repository.ServiceRepository serviceRepository;

    @Autowired
    private com.impactgraph.repository.DependencyRepository dependencyRepository;

    @Test
    @DisplayName("GET /api/projects/demo endpoint is publicly accessible and returns the demo project")
    void getDemoProjectEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/projects/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(DemoDataSeeder.DEMO_PROJECT_NAME));
    }

    @Test
    @DisplayName("Demo project produces exactly 8 services and 10 dependencies with valid source and target mappings")
    void demoProject_containsExpectedServicesAndDependencies() {
        Project seeded = demoDataSeeder.seedDemoProjectIfMissing();
        assertThat(seeded).isNotNull();

        List<com.impactgraph.entity.Service> services = serviceRepository.findByProjectId(seeded.getId());
        assertThat(services).hasSize(8);

        List<String> serviceNames = services.stream().map(com.impactgraph.entity.Service::getName).toList();
        assertThat(serviceNames).containsExactlyInAnyOrder(
                "Storefront Web",
                "Checkout Service",
                "Auth Service",
                "Order Service",
                "Payment Service",
                "Inventory Service",
                "Notification Service",
                "Audit Logger"
        );

        List<com.impactgraph.entity.Dependency> dependencies = dependencyRepository.findBySourceServiceProjectId(seeded.getId());
        assertThat(dependencies).hasSize(10);

        for (com.impactgraph.entity.Dependency dep : dependencies) {
            assertThat(dep.getSourceService()).isNotNull();
            assertThat(dep.getTargetService()).isNotNull();
            assertThat(serviceNames).contains(dep.getSourceService().getName());
            assertThat(serviceNames).contains(dep.getTargetService().getName());
        }
    }

    @Test
    @DisplayName("Seeded local demo credentials authenticate through the login endpoint")
    void demoCredentials_authenticateSuccessfully() throws Exception {
        demoDataSeeder.ensureDemoUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", DemoDataSeeder.DEMO_USER_EMAIL,
                                "password", DemoDataSeeder.DEMO_USER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DemoDataSeeder.DEMO_USER_EMAIL))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
