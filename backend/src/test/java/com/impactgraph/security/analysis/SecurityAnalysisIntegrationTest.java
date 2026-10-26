package com.impactgraph.security.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactgraph.dto.SecurityFindingDto;
import com.impactgraph.entity.AppUser;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.Role;
import com.impactgraph.repository.AnalysisRunRepository;
import com.impactgraph.repository.SecurityFindingRepository;
import com.impactgraph.repository.UserRepository;
import com.impactgraph.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAnalysisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityFindingRepository findingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AnalysisRunRepository runRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail("security.analyst@impactgraph.io")) {
            AppUser user = new AppUser();
            user.setEmail("security.analyst@impactgraph.io");
            user.setDisplayName("Security Analyst");
            user.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
            user.setRole(Role.DEVELOPER);
            user = userRepository.save(user);
            userToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        } else {
            AppUser user = userRepository.findByEmail("security.analyst@impactgraph.io").orElseThrow();
            userToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        }

        if (!userRepository.existsByEmail("security.admin@impactgraph.io")) {
            AppUser admin = new AppUser();
            admin.setEmail("security.admin@impactgraph.io");
            admin.setDisplayName("Security Admin");
            admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
            admin.setRole(Role.ADMIN);
            admin = userRepository.save(admin);
            adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), admin.getRole().name());
        } else {
            AppUser admin = userRepository.findByEmail("security.admin@impactgraph.io").orElseThrow();
            adminToken = jwtService.generateToken(admin.getId(), admin.getEmail(), admin.getRole().name());
        }
    }

    @Test
    void getFindings_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/security/findings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFindings_authenticated_returnsList() throws Exception {
        mockMvc.perform(get("/api/security/findings")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    void getApiInventory_authenticated_returnsInventoryList() throws Exception {
        mockMvc.perform(get("/api/security/api-inventory")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[*].path", hasItem("/api/health")))
                .andExpect(jsonPath("$[*].path", hasItem("/api/auth/login")));
    }

    @Test
    void triggerSecurityAnalysis_createsRunAndFindings() throws Exception {
        mockMvc.perform(post("/api/security/analyze")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("baseRevision", "develop")
                        .param("targetRevision", "feat/dashboard-redesign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.totalFindings").isNumber())
                .andExpect(jsonPath("$.apiInventory", not(empty())))
                .andExpect(jsonPath("$.recommendedTests", not(empty())));
    }

    @Test
    void getAiExplanation_returnsStructuredExplanation() throws Exception {
        var findings = findingRepository.findAll();
        UUID findingId = findings.get(0).getId();

        mockMvc.perform(get("/api/security/findings/" + findingId + "/ai-explanation")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value(findingId.toString()))
                .andExpect(jsonPath("$.threatSummary").isNotEmpty())
                .andExpect(jsonPath("$.remediationCodeSnippet").isNotEmpty())
                .andExpect(jsonPath("$.suggestedRegressionTests", not(empty())));
    }

    @Test
    void githubWebhookStatus_publicEndpoint_returnsStatus() throws Exception {
        mockMvc.perform(get("/api/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("/api/github/webhook"));
    }

    // =========================================================================
    // Cross-Tenant Access & Authorization Tests
    // =========================================================================

    @Test
    void securityEndpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/security/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/security/api-inventory")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/security/analyze")).andExpect(status().isUnauthorized());
    }

    @Test
    void getFindings_crossProject_developerWithoutMembership_returns403() throws Exception {
        mockMvc.perform(get("/api/security/findings")
                        .param("projectId", "88888")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getFindings_crossProject_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/security/findings")
                        .param("projectId", "88888")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void getHistory_crossProject_developerForbidden() throws Exception {
        mockMvc.perform(get("/api/security/history")
                        .param("projectId", "88888")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void analyze_crossProject_developerForbidden() throws Exception {
        mockMvc.perform(post("/api/security/analyze")
                        .param("projectId", "88888")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void findingScopedToProject_developerWithoutMembership_returns403() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-CONF-005");
        f.setTitle("Project-Scoped CORS Finding");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setProjectId(77777L);
        f = findingRepository.save(f);

        // Developer cannot view, verify, or get AI explanation
        mockMvc.perform(get("/api/security/findings/" + f.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/security/findings/" + f.getId() + "/ai-explanation")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // Admin can access
        mockMvc.perform(get("/api/security/findings/" + f.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Finding Verification Regression Tests (Vulnerability Present vs Remediated vs Missing)
    // =========================================================================

    @Test
    void verifyFinding_secTaint003_vulnerabilityRemainsPresent_failsVerification() throws Exception {
        // Finding 1 (demo fixture) targets ProjectController#getServicesForProject which lacks enforceProjectAccess
        var finding = findingRepository.findAll().stream()
                .filter(f -> "SEC-TAINT-003".equals(f.getRuleId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/security/findings/" + finding.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"))
                .andExpect(jsonPath("$.verificationMessage", containsString("Verification failed")));
    }

    @Test
    void verifyFinding_secTaint003_missingEvidenceFile_failsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-TAINT-003");
        f.setTitle("Missing Evidence Finding");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setFilePath("non/existent/MissingController.java");
        f.setAffectedEndpoint("GET /api/ghost/{id}");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"))
                .andExpect(jsonPath("$.verificationMessage", containsString("not found")));
    }

    @Test
    void verifyFinding_secAuthz002_remediated_succeedsVerification() throws Exception {
        // Finding 3 targets ProjectController#deleteProject which has !"ADMIN".equals(user.getRole())
        var finding = findingRepository.findAll().stream()
                .filter(f -> "SEC-AUTHZ-002".equals(f.getRuleId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/security/findings/" + finding.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(true))
                .andExpect(jsonPath("$.newStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verificationMessage", containsString("Verified")));
    }

    @Test
    void verifyFinding_secAuthz002_vulnerabilityPresent_failsVerification() throws Exception {
        // Target an endpoint without admin check: GET /api/projects/{projectId}/members
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-AUTHZ-002");
        f.setTitle("Unchecked Route");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setFilePath("backend/src/main/java/com/impactgraph/controller/ProjectController.java");
        f.setAffectedEndpoint("GET /api/projects/{projectId}/members");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"));
    }

    @Test
    void verifyFinding_secConf005_remediatedInSecurityConfig_succeedsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-CONF-005");
        f.setTitle("CORS Check");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setFilePath("backend/src/main/java/com/impactgraph/security/SecurityConfig.java");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(true))
                .andExpect(jsonPath("$.newStatus").value("VERIFIED"));
    }

    @Test
    void verifyFinding_secConf005_vulnerabilityPresent_failsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-CONF-005");
        f.setTitle("Wildcard CORS");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setFilePath("src/test/resources/VulnerableCorsConfig.java");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"))
                .andExpect(jsonPath("$.verificationMessage", containsString("wildcard '*'")));
    }

    @Test
    void verifyFinding_secConf005_missingFile_failsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-CONF-005");
        f.setTitle("Missing CORS Config");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setFilePath("non/existent/CorsConfig.java");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"));
    }

    @Test
    void verifyFinding_secAuth001_remediated_succeedsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-AUTH-001");
        f.setTitle("Mutating Endpoint Auth");
        f.setSeverity(com.impactgraph.entity.Severity.CRITICAL);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setAffectedEndpoint("POST /api/projects");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(true))
                .andExpect(jsonPath("$.newStatus").value("VERIFIED"));
    }

    @Test
    void verifyFinding_secAuth001_routeStillPermitAll_failsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-AUTH-001");
        f.setTitle("Public Health Endpoint");
        f.setSeverity(com.impactgraph.entity.Severity.HIGH);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f.setAffectedEndpoint("GET /api/health");
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"))
                .andExpect(jsonPath("$.verificationMessage", containsString("permits unauthenticated access")));
    }

    @Test
    void verifyFinding_secData004_conservativeOutcome_failsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-DATA-004");
        f.setTitle("Credential in Parameters");
        f.setSeverity(com.impactgraph.entity.Severity.MEDIUM);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"))
                .andExpect(jsonPath("$.verificationMessage", containsString("manual verification required")));
    }

    @Test
    void verifyFinding_unsupportedRule_conservativeOutcome_failsVerification() throws Exception {
        com.impactgraph.entity.SecurityFinding f = new com.impactgraph.entity.SecurityFinding();
        f.setRuleId("SEC-CUSTOM-999");
        f.setTitle("Unknown Rule Finding");
        f.setSeverity(com.impactgraph.entity.Severity.LOW);
        f.setConfidence(com.impactgraph.entity.Confidence.CONFIRMED);
        f.setStatus(FindingStatus.OPEN);
        f = findingRepository.save(f);

        mockMvc.perform(post("/api/security/findings/" + f.getId() + "/verify")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedResolved").value(false))
                .andExpect(jsonPath("$.newStatus").value("OPEN"))
                .andExpect(jsonPath("$.verificationMessage", containsString("not supported")));
    }

    // =========================================================================
    // Webhook Idempotency & Concurrency Tests
    // =========================================================================

    @Test
    void existingRecords_multipleNullDeliveryIds_coexistWithoutConstraintViolation() {
        com.impactgraph.entity.AnalysisRun run1 = new com.impactgraph.entity.AnalysisRun();
        run1.setRunType("MANUAL");
        run1.setStatus("COMPLETED");
        run1.setDeliveryId(null);
        runRepository.saveAndFlush(run1);

        com.impactgraph.entity.AnalysisRun run2 = new com.impactgraph.entity.AnalysisRun();
        run2.setRunType("GIT_REVISION");
        run2.setStatus("COMPLETED");
        run2.setDeliveryId(null);
        runRepository.saveAndFlush(run2);

        // Verify both runs with null deliveryId persist without unique constraint collision
        org.junit.jupiter.api.Assertions.assertNotNull(run1.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(run2.getId());
        org.junit.jupiter.api.Assertions.assertNotEquals(run1.getId(), run2.getId());
    }

    @Test
    void webhook_concurrentDuplicateDeliveries_handledIdempotently() throws Exception {
        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "number": 123,
                        "base": { "sha": "base111" },
                        "head": { "sha": "head222" }
                    },
                    "repository": {
                        "full_name": "AtharvKaner/ImpactGraph"
                    }
                }
                """;

        String deliveryId = "e2e-concurrent-delivery-" + java.util.UUID.randomUUID();
        int threads = 4;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<org.springframework.test.web.servlet.MvcResult>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return mockMvc.perform(post("/api/github/webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-GitHub-Delivery", deliveryId)
                                .content(payload))
                        .andReturn();
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        int processed = 0;
        int ignored = 0;
        for (var f : futures) {
            var result = f.get(5, java.util.concurrent.TimeUnit.SECONDS);
            String json = result.getResponse().getContentAsString();
            if (json.contains("\"PROCESSED\"")) processed++;
            if (json.contains("\"IGNORED\"")) ignored++;
        }
        executor.shutdown();

        org.junit.jupiter.api.Assertions.assertEquals(1, processed, "Exactly one webhook request should process");
        org.junit.jupiter.api.Assertions.assertEquals(threads - 1, ignored, "Duplicate concurrent requests should be ignored");
    }
}
