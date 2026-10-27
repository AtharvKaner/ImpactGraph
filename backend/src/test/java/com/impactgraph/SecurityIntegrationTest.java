package com.impactgraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactgraph.dto.*;
import com.impactgraph.entity.AuditEventType;
import com.impactgraph.repository.AuditEventRepository;
import com.impactgraph.repository.ProjectMembershipRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive security integration tests.
 * Uses H2 in-memory database — no external dependencies required.
 *
 * Tests cover:
 * 1. Valid login succeeds
 * 2. Invalid login fails without exposing sensitive info
 * 3. Unauthenticated access returns 401
 * 4. Authenticated user without required role receives 403
 * 5. Developer cannot access another project's resources (IDOR prevention)
 * 6. Developer cannot create/modify projects (admin-only)
 * 7. Authorized project access succeeds
 * 8. Security events are recorded
 * 9. Ordinary users cannot access audit records
 * 10. Health endpoint remains accessible
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMembershipRepository membershipRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private static String adminToken;
    private static String developerToken;
    private static String developer2Token;
    private static String projectId;

    private static java.util.UUID toProjectId(String id) {
        if (id == null) return null;
        try {
            return java.util.UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return new java.util.UUID(0L, Long.parseLong(id));
        }
    }

    @BeforeEach
    void cleanDatabase() {
        // Only clean before the first test
        if (adminToken == null) {
            auditEventRepository.deleteAll();
            membershipRepository.deleteAll();
            projectRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    // ========================
    // Test 10: Health endpoint
    // ========================

    @Test
    @Order(1)
    void healthEndpoint_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ImpactGraph Backend"));
    }

    // ========================
    // Test: Registration & Login
    // ========================

    @Test
    @Order(2)
    void register_firstUser_becomesAdmin() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin@test.com");
        request.setDisplayName("Test Admin");
        request.setPassword("SecurePass123!");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        adminToken = response.getToken();
        assertNotNull(adminToken);

        // Verify password hash is not in the response
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("SecurePass123!"));
    }

    @Test
    @Order(3)
    void register_secondUser_becomesDeveloper() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("dev@test.com");
        request.setDisplayName("Test Developer");
        request.setPassword("DevPass456!");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        developerToken = response.getToken();
    }

    @Test
    @Order(4)
    void register_thirdUser_developer2() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("dev2@test.com");
        request.setDisplayName("Test Developer 2");
        request.setPassword("DevPass789!");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        developer2Token = response.getToken();
    }

    // Test 1: Valid login succeeds
    @Test
    @Order(5)
    void login_validCredentials_succeeds() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("SecurePass123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        // Update admin token with fresh one
        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        adminToken = response.getToken();
    }

    // Test 2: Invalid login fails without exposing sensitive info
    @Test
    @Order(6)
    void login_invalidCredentials_failsGeneric() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                // Must NOT reveal whether email exists
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("BCrypt"))));
    }

    @Test
    @Order(7)
    void login_nonexistentEmail_sameGenericError() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@test.com");
        request.setPassword("SomePassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // Test 3: Unauthenticated access returns 401
    @Test
    @Order(8)
    void protectedEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    // Test 4: Authenticated user without required role receives 403
    @Test
    @Order(9)
    void adminEndpoint_developerRole_returns403() throws Exception {
        MembershipRequest request = new MembershipRequest();
        request.setUserEmail("dev@test.com");
        // Use a random UUID; we just need to verify role check
        request.setProjectId(java.util.UUID.randomUUID());

        mockMvc.perform(post("/api/admin/memberships")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test: Developer cannot create projects (admin only)
    @Test
    @Order(10)
    void createProject_developerRole_returns403() throws Exception {
        ProjectRequest request = new ProjectRequest();
        request.setName("Unauthorized Project");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Admin creates a project
    @Test
    @Order(11)
    void createProject_adminRole_succeeds() throws Exception {
        ProjectRequest request = new ProjectRequest();
        request.setName("Test Project Alpha");
        request.setDescription("A test project");

        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.name").value("Test Project Alpha"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        projectId = json.get("id").asText();
        assertNotNull(projectId);
    }

    // Test 7: Authorized project access succeeds (admin)
    @Test
    @Order(12)
    void getProject_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project Alpha"));
    }

    // Test 5: Developer cannot access a project they're not a member of (IDOR prevention)
    @Test
    @Order(13)
    void getProject_developerNotMember_returns403() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    // Grant membership to dev@test.com
    @Test
    @Order(14)
    void grantMembership_admin_succeeds() throws Exception {
        MembershipRequest request = new MembershipRequest();
        request.setUserEmail("dev@test.com");
        request.setProjectId(toProjectId(projectId));

        mockMvc.perform(post("/api/admin/memberships")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Membership granted"));
    }

    // Test 7: After membership, developer can access the project
    @Test
    @Order(15)
    void getProject_developerMember_succeeds() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project Alpha"));
    }

    // Test 5 continued: Developer 2 (not a member) still cannot access
    @Test
    @Order(16)
    void getProject_developer2NotMember_returns403() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developer2Token))
                .andExpect(status().isForbidden());
    }

    // Test 6: Developer cannot create or modify projects across boundaries
    @Test
    @Order(17)
    void updateProject_developerRole_returns403() throws Exception {
        ProjectRequest request = new ProjectRequest();
        request.setName("Hacked Name");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(18)
    void deleteProject_developerRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    // Test 9: Ordinary users cannot access audit records
    @Test
    @Order(19)
    void auditEndpoint_developerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    // Test 8: Security events are recorded
    @Test
    @Order(20)
    void auditEvents_recorded_forRelevantActions() throws Exception {
        // Admin can access audit events
        MvcResult result = mockMvc.perform(get("/api/audit/events?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        // Verify audit events were created for login, registration, project actions
        assertTrue(body.contains("LOGIN_SUCCESS"), "Should have LOGIN_SUCCESS event");
        assertTrue(body.contains("USER_REGISTERED"), "Should have USER_REGISTERED event");
        assertTrue(body.contains("PROJECT_CREATED"), "Should have PROJECT_CREATED event");
        assertTrue(body.contains("MEMBERSHIP_GRANTED"), "Should have MEMBERSHIP_GRANTED event");

        // Verify no passwords or tokens in audit records
        assertFalse(body.contains("SecurePass123!"), "Must not contain plaintext passwords");
        assertFalse(body.contains("DevPass456!"), "Must not contain plaintext passwords");
        assertFalse(body.contains("passwordHash"), "Must not contain password hashes");

        // Verify failed login was recorded
        assertTrue(body.contains("LOGIN_FAILURE"), "Should have LOGIN_FAILURE event");

        // Verify access denied events
        assertTrue(body.contains("ACCESS_DENIED"), "Should have ACCESS_DENIED event");
    }

    // Test 9 continued: No update/delete endpoints for audit
    @Test
    @Order(21)
    void auditEvents_noModificationEndpoints() throws Exception {
        // Attempt to POST/PUT/DELETE to audit endpoint — should fail
        mockMvc.perform(post("/api/audit/events")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(delete("/api/audit/events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    // Transparency endpoint
    @Test
    @Order(22)
    void transparencyPublic_isAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/transparency/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataCollected").isArray())
                .andExpect(jsonPath("$.dataStorage").isNotEmpty())
                .andExpect(jsonPath("$.accessControl").isNotEmpty())
                .andExpect(jsonPath("$.dataRetention").isNotEmpty());
    }

    @Test
    @Order(23)
    void transparencyMyActivity_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/transparency/my-activity"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(24)
    void transparencyMyActivity_showsOwnEvents() throws Exception {
        mockMvc.perform(get("/api/transparency/my-activity")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@test.com"))
                .andExpect(jsonPath("$.recentActivity").isArray());
    }

    // Validation tests
    @Test
    @Order(25)
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setDisplayName("Test");
        request.setPassword("ValidPass123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }

    @Test
    @Order(26)
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("valid@test.com");
        request.setDisplayName("Test");
        request.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    @Order(27)
    void register_duplicateEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin@test.com");
        request.setDisplayName("Duplicate");
        request.setPassword("AnotherPass123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    // List projects — developer sees only their projects
    @Test
    @Order(28)
    void listProjects_developer_seesOnlyMemberProjects() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode projects = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, projects.size(), "Developer should see only 1 project they are member of");
    }

    @Test
    @Order(29)
    void listProjects_developer2_seesNoProjects() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + developer2Token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode projects = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(0, projects.size(), "Developer 2 should see no projects");
    }

    // Revoke membership and verify access is denied
    @Test
    @Order(30)
    void revokeMembership_thenAccessDenied() throws Exception {
        MembershipRequest request = new MembershipRequest();
        request.setUserEmail("dev@test.com");
        request.setProjectId(toProjectId(projectId));

        // Revoke
        mockMvc.perform(delete("/api/admin/memberships")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Now developer should be denied
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // Additional Security & Robustness Tests
    // ==========================================

    @Test
    @Order(31)
    void jwt_malformedOrTampered_returns401() throws Exception {
        // Malformed token
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer invalid.malformed.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        // Tampered token (change signature)
        String tamperedToken = developerToken.substring(0, developerToken.length() - 5) + "abcde";
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @Order(32)
    void malformedPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json-structure"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request payload"));
    }

    @Test
    @Order(33)
    void malformedResourceId_returns400() throws Exception {
        mockMvc.perform(get("/api/projects/not-a-valid-uuid")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter: projectId"));
    }

    @Test
    @Order(34)
    void auditEvents_developerRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(35)
    void crossProjectAccess_twoIsolatedProjectsAndUsers() throws Exception {
        // Create second project: Project Beta
        ProjectRequest betaReq = new ProjectRequest();
        betaReq.setName("Test Project Beta");
        betaReq.setDescription("Isolated project for dev 2");

        MvcResult betaResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(betaReq)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        String projectBetaId = objectMapper.readTree(betaResult.getResponse().getContentAsString())
                .get("id").asText();

        // Grant dev@test.com access back to Project Alpha
        MembershipRequest alphaGrant = new MembershipRequest();
        alphaGrant.setUserEmail("dev@test.com");
        alphaGrant.setProjectId(toProjectId(projectId));
        mockMvc.perform(post("/api/admin/memberships")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alphaGrant)))
                .andExpect(status().isOk());

        // Grant dev2@test.com access ONLY to Project Beta
        MembershipRequest betaGrant = new MembershipRequest();
        betaGrant.setUserEmail("dev2@test.com");
        betaGrant.setProjectId(toProjectId(projectBetaId));
        mockMvc.perform(post("/api/admin/memberships")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(betaGrant)))
                .andExpect(status().isOk());

        // dev@test.com CAN access Project Alpha, CANNOT access Project Beta
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project Alpha"));

        mockMvc.perform(get("/api/projects/" + projectBetaId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        // dev2@test.com CAN access Project Beta, CANNOT access Project Alpha
        mockMvc.perform(get("/api/projects/" + projectBetaId)
                        .header("Authorization", "Bearer " + developer2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project Beta"));

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + developer2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }
}
