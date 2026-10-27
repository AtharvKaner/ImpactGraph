package com.impactgraph.security.analysis;

import com.impactgraph.dto.ApiInventoryItemDto;
import com.impactgraph.entity.Confidence;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Deterministic rule engine running structured security rules against analyzed APIs and source code.
 */
@Service
public class SecurityRuleEngine {

    private final List<SecurityRule> registeredRules = new ArrayList<>();

    public SecurityRuleEngine() {
        registerRule(new AuthenticationRegressionRule());
        registerRule(new AuthorizationCheckRegressionRule());
        registerRule(new UntrustedInputIdorRule());
        registerRule(new SensitiveDataExposureRule());
        registerRule(new SecurityConfigurationRegressionRule());
        registerRule(new SharedSecurityComponentDriftRule());
    }

    public void registerRule(SecurityRule rule) {
        registeredRules.add(rule);
    }

    public List<SecurityRule> getRegisteredRules() {
        return Collections.unmodifiableList(registeredRules);
    }

    /**
     * Executes all rules against the provided analysis context.
     */
    public List<SecurityFinding> evaluateAll(
            List<ApiInventoryItemDto> apis,
            JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
            String baseRevision,
            String targetRevision,
            Long projectId
    ) {
        List<SecurityFinding> findings = new ArrayList<>();
        for (SecurityRule rule : registeredRules) {
            List<SecurityFinding> ruleFindings = rule.evaluate(apis, sourceResult, baseRevision, targetRevision);
            for (SecurityFinding f : ruleFindings) {
                f.setProjectId(projectId);
                findings.add(f);
            }
        }
        return findings;
    }

    // =========================================================================
    // Rule 1: SEC-AUTH-001: API Authentication Regression
    // =========================================================================
    private static class AuthenticationRegressionRule implements SecurityRule {
        @Override
        public String getRuleId() { return "SEC-AUTH-001"; }

        @Override
        public String getTitle() { return "API Authentication Regression / Unprotected Mutating Endpoint"; }

        @Override
        public Severity getDefaultSeverity() { return Severity.CRITICAL; }

        @Override
        public List<SecurityFinding> evaluate(
                List<ApiInventoryItemDto> currentApis,
                JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
                String baseRevision,
                String targetRevision
        ) {
            List<SecurityFinding> findings = new ArrayList<>();
            for (ApiInventoryItemDto api : currentApis) {
                // If a mutating method is marked PUBLIC and is not login/register/webhook
                boolean isPublic = "PUBLIC".equals(api.authRequirement()) || api.isPermitAll();
                boolean isMutating = "POST".equals(api.method()) || "PUT".equals(api.method()) || "DELETE".equals(api.method());
                boolean isPermittedPublic = api.path().startsWith("/api/auth/") || api.path().equals("/api/github/webhook");

                if (isPublic && isMutating && !isPermittedPublic) {
                    SecurityFinding f = new SecurityFinding();
                    f.setRuleId(getRuleId());
                    f.setTitle("Unauthenticated mutating endpoint exposed: " + api.method() + " " + api.path());
                    f.setDescription("Endpoint accepts state-modifying requests without requiring authentication, allowing anonymous callers to manipulate data.");
                    f.setSeverity(Severity.CRITICAL);
                    f.setConfidence(Confidence.CONFIRMED);
                    f.setStatus(FindingStatus.OPEN);
                    f.setBaseRevision(baseRevision);
                    f.setTargetRevision(targetRevision);
                    f.setAffectedEndpoint(api.method() + " " + api.path());
                    f.setHttpMethod(api.method());
                    f.setAffectedComponent(api.controllerName());
                    f.setEvidencePath(api.controllerName() + "#" + api.handlerMethod() + " mapped to " + api.path() + " [permitAll]");
                    f.setExplanation("Spring Security permits unauthenticated access to a mutating HTTP route. All data-modifying endpoints must enforce JWT token verification.");
                    f.setSuggestedRemediation("Ensure route " + api.path() + " requires authentication in SecurityConfig: .requestMatchers(\"" + api.path() + "\").authenticated()");
                    f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest#unauthenticatedAccess_rejectedWith401");
                    f.setCreatedAt(Instant.now());
                    findings.add(f);
                } else if ("REGRESSED".equals(api.driftStatus())) {
                    SecurityFinding f = new SecurityFinding();
                    f.setRuleId(getRuleId());
                    f.setTitle("Authentication requirement removed: " + api.method() + " " + api.path());
                    f.setDescription("Endpoint was previously protected by authentication but is now publicly accessible in the target revision.");
                    f.setSeverity(Severity.HIGH);
                    f.setConfidence(Confidence.CONFIRMED);
                    f.setStatus(FindingStatus.OPEN);
                    f.setBaseRevision(baseRevision);
                    f.setTargetRevision(targetRevision);
                    f.setAffectedEndpoint(api.method() + " " + api.path());
                    f.setHttpMethod(api.method());
                    f.setAffectedComponent(api.controllerName());
                    f.setEvidencePath("SecurityConfig drift: previously authenticated -> permitAll");
                    f.setExplanation("Detected removal of authentication filter requirement between base and target revisions.");
                    f.setSuggestedRemediation("Restore authentication check in SecurityConfig.");
                    f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest");
                    f.setCreatedAt(Instant.now());
                    findings.add(f);
                }
            }
            return findings;
        }
    }

    // =========================================================================
    // Rule 2: SEC-AUTHZ-002: Authorization Check Regression
    // =========================================================================
    private static class AuthorizationCheckRegressionRule implements SecurityRule {
        @Override
        public String getRuleId() { return "SEC-AUTHZ-002"; }

        @Override
        public String getTitle() { return "Missing or Weakened Role-Based Access Control (RBAC)"; }

        @Override
        public Severity getDefaultSeverity() { return Severity.HIGH; }

        @Override
        public List<SecurityFinding> evaluate(
                List<ApiInventoryItemDto> currentApis,
                JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
                String baseRevision,
                String targetRevision
        ) {
            List<SecurityFinding> findings = new ArrayList<>();
            for (JavaSourceAnalyzer.EndpointDefinition ep : sourceResult.endpoints()) {
                boolean isPrivilegedPath = ep.fullPath().startsWith("/api/admin") || ep.fullPath().startsWith("/api/audit");
                boolean hasRoleProtection = (ep.preAuthorizeExpr() != null && ep.preAuthorizeExpr().contains("ADMIN"))
                        || (ep.preAuthorizeExpr() != null && ep.preAuthorizeExpr().contains("hasRole"));

                if (isPrivilegedPath && !hasRoleProtection) {
                    SecurityFinding f = new SecurityFinding();
                    f.setRuleId(getRuleId());
                    f.setTitle("Privileged route missing explicit @PreAuthorize: " + ep.httpMethod() + " " + ep.fullPath());
                    f.setDescription("Administrative or audit endpoints should have defense-in-depth method security with @PreAuthorize(\"hasRole('ADMIN')\").");
                    f.setSeverity(Severity.HIGH);
                    f.setConfidence(Confidence.HIGH);
                    f.setStatus(FindingStatus.OPEN);
                    f.setBaseRevision(baseRevision);
                    f.setTargetRevision(targetRevision);
                    f.setFilePath(ep.sourceFilePath());
                    f.setStartLine(ep.lineNumber());
                    f.setAffectedEndpoint(ep.httpMethod() + " " + ep.fullPath());
                    f.setHttpMethod(ep.httpMethod());
                    f.setAffectedComponent(ep.controllerName());
                    f.setEvidencePath(ep.controllerName() + "#" + ep.handlerMethod() + " lacks @PreAuthorize(\"hasRole('ADMIN')\")");
                    f.setExplanation("Relying solely on URL pattern matchers without method-level annotations risks privilege escalation if URL routing patterns change.");
                    f.setSuggestedRemediation("Annotate method or class with @PreAuthorize(\"hasRole('ADMIN')\").");
                    f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest#grantMembership_admin_succeeds");
                    f.setCreatedAt(Instant.now());
                    findings.add(f);
                }
            }
            return findings;
        }
    }

    // =========================================================================
    // Rule 3: SEC-TAINT-003: Untrusted Identifier / Missing IDOR Enforcement
    // =========================================================================
    private static class UntrustedInputIdorRule implements SecurityRule {
        @Override
        public String getRuleId() { return "SEC-TAINT-003"; }

        @Override
        public String getTitle() { return "Potential Insecure Direct Object Reference (IDOR) / Missing Tenant Access Check"; }

        @Override
        public Severity getDefaultSeverity() { return Severity.HIGH; }

        @Override
        public List<SecurityFinding> evaluate(
                List<ApiInventoryItemDto> currentApis,
                JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
                String baseRevision,
                String targetRevision
        ) {
            List<SecurityFinding> findings = new ArrayList<>();
            for (JavaSourceAnalyzer.EndpointDefinition ep : sourceResult.endpoints()) {
                boolean hasResourceId = ep.fullPath().contains("{projectId}") || ep.fullPath().contains("{id}");
                if (hasResourceId && !ep.fullPath().startsWith("/api/admin")) {
                    // Check if it passes authentication principal and enforces access
                    boolean invokesAccessCheck = ep.invokedMethods().stream().anyMatch(m ->
                            m.contains("enforceProjectAccess") || m.contains("canAccessProject") || m.contains("checkMembership"));

                    if (!ep.hasAuthenticationPrincipal() && !invokesAccessCheck) {
                        SecurityFinding f = new SecurityFinding();
                        f.setRuleId(getRuleId());
                        f.setTitle("Project-scoped endpoint lacks tenant access verification: " + ep.httpMethod() + " " + ep.fullPath());
                        f.setDescription("Endpoint accepts resource identifier without verifying caller has been granted membership to the specified project.");
                        f.setSeverity(Severity.HIGH);
                        f.setConfidence(Confidence.MEDIUM);
                        f.setStatus(FindingStatus.OPEN);
                        f.setBaseRevision(baseRevision);
                        f.setTargetRevision(targetRevision);
                        f.setFilePath(ep.sourceFilePath());
                        f.setStartLine(ep.lineNumber());
                        f.setAffectedEndpoint(ep.httpMethod() + " " + ep.fullPath());
                        f.setHttpMethod(ep.httpMethod());
                        f.setAffectedComponent(ep.controllerName());
                        f.setEvidencePath(ep.controllerName() + "#" + ep.handlerMethod() + " accepts resource ID without enforceProjectAccess check");
                        f.setExplanation("A user with a valid JWT could query or mutate resources belonging to another project by tampering with the URL parameter.");
                        f.setSuggestedRemediation("Inject @AuthenticationPrincipal JwtAuthenticatedUser user and call projectService.enforceProjectAccess(user, projectId).");
                        f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest#accessOtherProject_forbidden");
                        f.setCreatedAt(Instant.now());
                        findings.add(f);
                    }
                }
            }
            return findings;
        }
    }

    // =========================================================================
    // Rule 4: SEC-DATA-004: Sensitive Credential or Token Exposure
    // =========================================================================
    private static class SensitiveDataExposureRule implements SecurityRule {
        @Override
        public String getRuleId() { return "SEC-DATA-004"; }

        @Override
        public String getTitle() { return "Potential Sensitive Credential or Token Leakage in DTO / Logs"; }

        @Override
        public Severity getDefaultSeverity() { return Severity.MEDIUM; }

        @Override
        public List<SecurityFinding> evaluate(
                List<ApiInventoryItemDto> currentApis,
                JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
                String baseRevision,
                String targetRevision
        ) {
            List<SecurityFinding> findings = new ArrayList<>();
            for (ApiInventoryItemDto api : currentApis) {
                if (api.sensitiveFields() != null && !api.sensitiveFields().isEmpty() && !api.path().startsWith("/api/auth/")) {
                    SecurityFinding f = new SecurityFinding();
                    f.setRuleId(getRuleId());
                    f.setTitle("Sensitive parameter detected in endpoint: " + api.method() + " " + api.path());
                    f.setDescription("Endpoint accepts or processes sensitive parameters (" + String.join(", ", api.sensitiveFields()) + ") outside the core auth controllers.");
                    f.setSeverity(Severity.MEDIUM);
                    f.setConfidence(Confidence.MEDIUM);
                    f.setStatus(FindingStatus.OPEN);
                    f.setBaseRevision(baseRevision);
                    f.setTargetRevision(targetRevision);
                    f.setAffectedEndpoint(api.method() + " " + api.path());
                    f.setHttpMethod(api.method());
                    f.setAffectedComponent(api.controllerName());
                    f.setEvidencePath(api.controllerName() + " sensitive parameters: " + api.sensitiveFields());
                    f.setExplanation("Credentials or secrets should only be processed by dedicated authentication/credential services and never logged.");
                    f.setSuggestedRemediation("Ensure sensitive parameters are encrypted or encapsulated in secured transfer objects.");
                    f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest");
                    f.setCreatedAt(Instant.now());
                    findings.add(f);
                }
            }
            return findings;
        }
    }

    // =========================================================================
    // Rule 5: SEC-CONF-005: Security Configuration Regression
    // =========================================================================
    private static class SecurityConfigurationRegressionRule implements SecurityRule {
        @Override
        public String getRuleId() { return "SEC-CONF-005"; }

        @Override
        public String getTitle() { return "Security Configuration / CORS / Secret Weakening"; }

        @Override
        public Severity getDefaultSeverity() { return Severity.HIGH; }

        @Override
        public List<SecurityFinding> evaluate(
                List<ApiInventoryItemDto> currentApis,
                JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
                String baseRevision,
                String targetRevision
        ) {
            List<SecurityFinding> findings = new ArrayList<>();
            String secConfig = sourceResult.securityConfigurations().get("SecurityConfig");
            if (secConfig != null) {
                if (secConfig.contains("\"*\"") && (secConfig.contains("Origin") || secConfig.contains("cors") || secConfig.contains("Cors"))) {
                    SecurityFinding f = new SecurityFinding();
                    f.setRuleId(getRuleId());
                    f.setTitle("Permissive Wildcard CORS Configuration Detected");
                    f.setDescription("CORS allows wildcard origins, permitting any third-party domain to initiate cross-origin requests.");
                    f.setSeverity(Severity.HIGH);
                    f.setConfidence(Confidence.CONFIRMED);
                    f.setStatus(FindingStatus.OPEN);
                    f.setBaseRevision(baseRevision);
                    f.setTargetRevision(targetRevision);
                    f.setFilePath("backend/src/main/java/com/impactgraph/security/SecurityConfig.java");
                    f.setAffectedComponent("SecurityConfig");
                    f.setEvidencePath("SecurityConfig.java -> corsConfigurationSource() allows '*'");
                    f.setExplanation("Wildcard origins in CORS can allow malicious web pages to execute authenticated cross-origin requests on behalf of users.");
                    f.setSuggestedRemediation("Restrict CORS origins to explicit, trusted frontend origins (e.g. http://localhost:5173).");
                    f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest#corsHeaders_restricted");
                    f.setCreatedAt(Instant.now());
                    findings.add(f);
                }
            }
            return findings;
        }
    }

    // =========================================================================
    // Rule 6: SEC-DEPS-006: High-Impact Shared Security Component Drift
    // =========================================================================
    private static class SharedSecurityComponentDriftRule implements SecurityRule {
        @Override
        public String getRuleId() { return "SEC-DEPS-006"; }

        @Override
        public String getTitle() { return "High-Blast-Radius Shared Security Component Modification"; }

        @Override
        public Severity getDefaultSeverity() { return Severity.MEDIUM; }

        @Override
        public List<SecurityFinding> evaluate(
                List<ApiInventoryItemDto> currentApis,
                JavaSourceAnalyzer.SourceAnalysisResult sourceResult,
                String baseRevision,
                String targetRevision
        ) {
            List<SecurityFinding> findings = new ArrayList<>();
            // Triggered when core auth components are part of the target revision change
            if (targetRevision != null && (targetRevision.contains("auth") || targetRevision.contains("security"))) {
                SecurityFinding f = new SecurityFinding();
                f.setRuleId(getRuleId());
                f.setTitle("Shared authentication/authorization layer modified");
                f.setDescription("Modifications in security filters, token providers, or RBAC interceptors affect downstream blast radius across all services.");
                f.setSeverity(Severity.INFO);
                f.setConfidence(Confidence.HIGH);
                f.setStatus(FindingStatus.OPEN);
                f.setBaseRevision(baseRevision);
                f.setTargetRevision(targetRevision);
                f.setAffectedComponent("SecurityConfig / JwtAuthenticationFilter");
                f.setEvidencePath("Core security configuration modified in revision " + targetRevision);
                f.setExplanation("Because SecurityConfig guards all 10+ API endpoints, changes must be verified against the entire regression test suite.");
                f.setSuggestedRemediation("Execute the comprehensive 35-test SecurityIntegrationTest suite to verify no regressions occurred.");
                f.setSuggestedTests("com.impactgraph.SecurityIntegrationTest");
                f.setCreatedAt(Instant.now());
                findings.add(f);
            }
            return findings;
        }
    }
}
