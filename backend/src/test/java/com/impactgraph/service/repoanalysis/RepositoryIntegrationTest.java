package com.impactgraph.service.repoanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactgraph.dto.AuthResponse;
import com.impactgraph.dto.RegisterRequest;
import com.impactgraph.dto.repoanalysis.RepositoryImportRequest;
import com.impactgraph.dto.repoanalysis.RevisionComparisonRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RepositoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String otherUserToken;

    @BeforeEach
    void setupUsers() throws Exception {
        if (userToken == null) {
            String email1 = "repo-tester-" + UUID.randomUUID() + "@test.com";
            RegisterRequest req1 = new RegisterRequest();
            req1.setEmail(email1);
            req1.setDisplayName("Repo Tester");
            req1.setPassword("ValidPassword123!");

            MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isOk())
                    .andReturn();
            AuthResponse auth1 = objectMapper.readValue(res1.getResponse().getContentAsString(), AuthResponse.class);
            userToken = auth1.getToken();

            String email2 = "repo-other-" + UUID.randomUUID() + "@test.com";
            RegisterRequest req2 = new RegisterRequest();
            req2.setEmail(email2);
            req2.setDisplayName("Other Dev");
            req2.setPassword("ValidPassword123!");

            MvcResult res2 = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isOk())
                    .andReturn();
            AuthResponse auth2 = objectMapper.readValue(res2.getResponse().getContentAsString(), AuthResponse.class);
            otherUserToken = auth2.getToken();
        }
    }

    @Test
    @DisplayName("Unauthenticated repository import request returns 401 Unauthorized")
    void importRepository_unauthenticated_returns401() throws Exception {
        RepositoryImportRequest request = new RepositoryImportRequest();
        request.setSourceType("GITHUB");
        request.setRepositoryUrl("https://github.com/spring-projects/spring-petclinic");

        mockMvc.perform(post("/api/repositories/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Importing repository with SSRF / malicious URL returns 400 Bad Request")
    void importRepository_maliciousUrl_returns400() throws Exception {
        RepositoryImportRequest request = new RepositoryImportRequest();
        request.setSourceType("GITHUB");
        request.setRepositoryUrl("https://127.0.0.1/malicious/repo");

        mockMvc.perform(post("/api/repositories/import")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Authenticated user imports local repository, grants membership, and accesses project")
    void importRepository_localWorkspace_succeedsAndGrantsAccess() throws Exception {
        // Create an isolated small workspace fixture on disk
        Path fixtureDir = Files.createTempDirectory("fixture-repo-");
        try {
            Path srcDir = fixtureDir.resolve("src/main/java/com/test");
            Files.createDirectories(srcDir);

            Files.writeString(srcDir.resolve("PaymentController.java"), """
                    package com.test;
                    import org.springframework.web.bind.annotation.*;
                    @RestController
                    @RequestMapping("/api/payments")
                    public class PaymentController {
                        @GetMapping("/{id}")
                        public String getPayment() { return "OK"; }
                    }
                    """);

            RepositoryImportRequest request = new RepositoryImportRequest();
            request.setSourceType("LOCAL");
            request.setLocalPath(fixtureDir.toAbsolutePath().toString());
            request.setProjectName("Local Test Fixture " + UUID.randomUUID());

            MvcResult result = mockMvc.perform(post("/api/repositories/import")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.projectId").isNumber())
                    .andExpect(jsonPath("$.serviceCount").value(1))
                    .andExpect(jsonPath("$.apiCount").value(1))
                    .andReturn();

            Number projectId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("projectId").numberValue();

            // Verify importing user can access the project
            mockMvc.perform(get("/api/projects/" + projectId)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(projectId));

            // Verify other user without membership receives 403 Forbidden (IDOR prevention)
            mockMvc.perform(get("/api/projects/" + projectId)
                            .header("Authorization", "Bearer " + otherUserToken))
                    .andExpect(status().isForbidden());

        } finally {
            new GitOperationsService().deleteDirectoryRecursively(fixtureDir);
        }
    }

    @Test
    @DisplayName("Local workspace info endpoint returns path and repository status")
    void getLocalWorkspaceInfo_returnsCurrentDirectoryMetadata() throws Exception {
        mockMvc.perform(get("/api/repositories/local-workspace")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").isNotEmpty())
                .andExpect(jsonPath("$.isAuthorized").value(true));
    }
}
