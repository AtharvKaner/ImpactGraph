package com.impactgraph.security.analysis;

import com.impactgraph.dto.AiExplanationDto;
import com.impactgraph.entity.SecurityFinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-assisted security explanation and remediation service.
 * <p>
 * Produces deterministic, offline remediation guidance from the finding's static evidence.
 */
@Service
public class DefaultAiExplanationService implements AiExplanationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiExplanationService.class);

    @Override
    public boolean isAiProviderConfigured() {
        return false;
    }

    @Override
    public AiExplanationDto explainFinding(SecurityFinding finding) {
        if (finding == null) {
            return null;
        }

        // Deterministic expert advice baseline
        String ruleId = finding.getRuleId();
        String threatSummary;
        String impactRationale;
        String remediationSnippet;
        List<String> suggestedTests = new ArrayList<>();

        if ("SEC-AUTH-001".equals(ruleId)) {
            threatSummary = "An anonymous attacker can invoke state-changing APIs without credentials, enabling unauthorized data modification or administrative abuse.";
            impactRationale = "Spring Security filter chain bypass allows arbitrary HTTP callers to access the endpoint without presenting a valid JWT bearer token.";
            remediationSnippet = """
                    // In SecurityConfig.java:
                    .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/auth/**").permitAll()
                        .requestMatchers("%s").authenticated() // Enforce authentication
                        .anyRequest().authenticated()
                    );
                    """.formatted(finding.getAffectedEndpoint() != null ? finding.getAffectedEndpoint() : "/api/target");
            suggestedTests.add("SecurityIntegrationTest#unauthenticatedAccess_rejectedWith401");
        } else if ("SEC-AUTHZ-002".equals(ruleId)) {
            threatSummary = "A low-privileged user (e.g. DEVELOPER) can perform high-privileged administrative actions (e.g. user management or project deletions).";
            impactRationale = "Endpoint lacks explicit role verification in @PreAuthorize, allowing any authenticated caller with a valid JWT to execute privileged operations.";
            remediationSnippet = """
                    // In Controller class or method:
                    @PreAuthorize("hasRole('ADMIN')")
                    @PostMapping("/admin/operation")
                    public ResponseEntity<?> privilegedAction(...) { ... }
                    """;
            suggestedTests.add("SecurityIntegrationTest#createProject_developer_accessDenied");
            suggestedTests.add("SecurityIntegrationTest#grantMembership_admin_succeeds");
        } else if ("SEC-TAINT-003".equals(ruleId)) {
            threatSummary = "Insecure Direct Object Reference (IDOR): A user can access or manipulate resources of another organization or project by guessing or tampering with the URL identifier.";
            impactRationale = "The controller extracts the resource ID directly from the path variable without checking that the caller belongs to the tenant project.";
            remediationSnippet = """
                    // In Service layer:
                    public void enforceProjectAccess(JwtAuthenticatedUser user, Long projectId) {
                        if (!canAccessProject(user, projectId)) {
                            auditService.logEvent(AuditEventType.ACCESS_DENIED, ...);
                            throw new SecurityException("You do not have access to this project");
                        }
                    }
                    """;
            suggestedTests.add("SecurityIntegrationTest#accessOtherProject_forbidden");
        } else if ("SEC-DATA-004".equals(ruleId)) {
            threatSummary = "Sensitive data exposure: Internal tokens, passwords, or hashes could be leaked in HTTP response bodies or error payloads.";
            impactRationale = "Serializing internal database entities directly to JSON often exposes fields like passwordHash, secret keys, or internal keys.";
            remediationSnippet = """
                    // Return a dedicated DTO omitting sensitive fields:
                    public record UserProfileResponse(UUID id, String email, String displayName, String role) {
                        public static UserProfileResponse from(AppUser u) {
                            return new UserProfileResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name());
                        }
                    }
                    """;
            suggestedTests.add("SecurityIntegrationTest#userResponse_omitsPasswordHash");
        } else {
            threatSummary = "Architectural security control regression identified in code revision.";
            impactRationale = finding.getDescription() != null ? finding.getDescription() : "Security property violated.";
            remediationSnippet = "// Review affected component and apply defense-in-depth principles.";
            suggestedTests.add("SecurityIntegrationTest");
        }

        return new AiExplanationDto(
                finding.getId(),
                ruleId,
                threatSummary,
                impactRationale,
                finding.getSuggestedRemediation() != null ? finding.getSuggestedRemediation() : "Apply remediation snippet.",
                remediationSnippet,
                suggestedTests,
                "Deterministic analysis based on Spring Security AST evidence. Static graph reachability does not guarantee exploitability without runtime validation.",
                false,
                "Deterministic Security Knowledge Engine (offline)"
        );
    }
}
