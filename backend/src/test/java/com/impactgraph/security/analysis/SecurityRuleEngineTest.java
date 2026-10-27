package com.impactgraph.security.analysis;

import com.impactgraph.dto.ApiInventoryItemDto;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityRuleEngineTest {

    private SecurityRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new SecurityRuleEngine();
    }

    @Test
    void evaluatesAllRegisteredRules() {
        assertEquals(6, ruleEngine.getRegisteredRules().size());
    }

    @Test
    void rule1_flagsUnauthenticatedMutatingEndpoint() {
        // Positive fixture: mutating endpoint marked PUBLIC
        ApiInventoryItemDto badEp = new ApiInventoryItemDto(
                "POST", "/api/vulnerable/delete", "BadController", "deleteSomething",
                "PUBLIC", null, false, true, "permitAll", List.of(), "NEW", "CRITICAL"
        );

        JavaSourceAnalyzer.SourceAnalysisResult emptySource = new JavaSourceAnalyzer.SourceAnalysisResult(
                List.of(), Map.of(), Map.of(), 0
        );

        List<SecurityFinding> findings = ruleEngine.evaluateAll(
                List.of(badEp), emptySource, "main", "feature", null
        );

        assertTrue(findings.stream().anyMatch(f -> "SEC-AUTH-001".equals(f.getRuleId())));
        SecurityFinding authFinding = findings.stream().filter(f -> "SEC-AUTH-001".equals(f.getRuleId())).findFirst().orElseThrow();
        assertEquals(Severity.CRITICAL, authFinding.getSeverity());
        assertEquals("POST", authFinding.getHttpMethod());
    }

    @Test
    void rule1_doesNotFlagLegitimatePublicEndpoints() {
        // Negative fixture: safe login endpoint is public
        ApiInventoryItemDto safeLogin = new ApiInventoryItemDto(
                "POST", "/api/auth/login", "AuthController", "login",
                "PUBLIC", null, false, true, "permitAll", List.of(), "UNCHANGED", "SAFE"
        );

        JavaSourceAnalyzer.SourceAnalysisResult emptySource = new JavaSourceAnalyzer.SourceAnalysisResult(
                List.of(), Map.of(), Map.of(), 0
        );

        List<SecurityFinding> findings = ruleEngine.evaluateAll(
                List.of(safeLogin), emptySource, "main", "feature", null
        );

        assertFalse(findings.stream().anyMatch(f -> "SEC-AUTH-001".equals(f.getRuleId())),
                "Safe /api/auth/login should not trigger SEC-AUTH-001");
    }

    @Test
    void rule2_flagsPrivilegedEndpointMissingPreAuthorize() {
        JavaSourceAnalyzer.EndpointDefinition adminEp = new JavaSourceAnalyzer.EndpointDefinition(
                "AdminController", "resetDatabase", "POST", "/api/admin/reset",
                null, false, List.of(), "AdminController.java", 20, List.of(), List.of()
        );

        JavaSourceAnalyzer.SourceAnalysisResult sourceResult = new JavaSourceAnalyzer.SourceAnalysisResult(
                List.of(adminEp), Map.of(), Map.of(), 1
        );

        List<SecurityFinding> findings = ruleEngine.evaluateAll(
                List.of(), sourceResult, "main", "feature", null
        );

        assertTrue(findings.stream().anyMatch(f -> "SEC-AUTHZ-002".equals(f.getRuleId())),
                "Should flag /api/admin/reset missing @PreAuthorize");
    }

    @Test
    void rule3_flagsProjectEndpointMissingAccessCheck() {
        JavaSourceAnalyzer.EndpointDefinition idorEp = new JavaSourceAnalyzer.EndpointDefinition(
                "DataController", "getProjectData", "GET", "/api/projects/{projectId}/secrets",
                null, false, List.of("Long projectId"), "DataController.java", 35,
                List.of("repository#findAll"), List.of()
        );

        JavaSourceAnalyzer.SourceAnalysisResult sourceResult = new JavaSourceAnalyzer.SourceAnalysisResult(
                List.of(idorEp), Map.of(), Map.of(), 1
        );

        List<SecurityFinding> findings = ruleEngine.evaluateAll(
                List.of(), sourceResult, "main", "feature", null
        );

        assertTrue(findings.stream().anyMatch(f -> "SEC-TAINT-003".equals(f.getRuleId())),
                "Should flag IDOR endpoint lacking project access check");
    }

    @Test
    void rule5_flagsWildcardCorsConfiguration() {
        String badCorsConfig = """
                @Bean
                public CorsConfigurationSource corsConfigurationSource() {
                    CorsConfiguration configuration = new CorsConfiguration();
                    configuration.setAllowedOriginPatterns(List.of("*"));
                    return source;
                }
                """;

        JavaSourceAnalyzer.SourceAnalysisResult sourceResult = new JavaSourceAnalyzer.SourceAnalysisResult(
                List.of(), Map.of(), Map.of("SecurityConfig", badCorsConfig), 1
        );

        List<SecurityFinding> findings = ruleEngine.evaluateAll(
                List.of(), sourceResult, "main", "feature", null
        );

        assertTrue(findings.stream().anyMatch(f -> "SEC-CONF-005".equals(f.getRuleId())),
                "Should flag wildcard CORS configuration in SecurityConfig");
    }
}
