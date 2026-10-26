package com.impactgraph.security.analysis;

import com.impactgraph.entity.SecurityFinding;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maps security findings, affected components, and rules to concrete targeted
 * regression test suites.
 */
@Service
public class TargetedTestSelector {

    public record TestRecommendation(
            String testClass,
            String testMethod,
            String rationale,
            String targetComponent
    ) {}

    public List<TestRecommendation> recommendTests(List<SecurityFinding> findings, List<String> affectedComponents) {
        Set<String> seen = new HashSet<>();
        List<TestRecommendation> recommendations = new ArrayList<>();

        for (SecurityFinding f : findings) {
            String ruleId = f.getRuleId();
            if ("SEC-AUTH-001".equals(ruleId)) {
                addIfAbsent(recommendations, seen, new TestRecommendation(
                        "com.impactgraph.SecurityIntegrationTest",
                        "health_public_succeeds / unauthenticated_rejected",
                        "Verifies that authentication filters reject anonymous requests for protected routes",
                        f.getAffectedComponent()
                ));
            } else if ("SEC-AUTHZ-002".equals(ruleId)) {
                addIfAbsent(recommendations, seen, new TestRecommendation(
                        "com.impactgraph.SecurityIntegrationTest",
                        "createProject_developer_accessDenied",
                        "Verifies that DEVELOPER role cannot execute ADMIN-only operations",
                        f.getAffectedComponent()
                ));
                addIfAbsent(recommendations, seen, new TestRecommendation(
                        "com.impactgraph.SecurityIntegrationTest",
                        "grantMembership_admin_succeeds",
                        "Verifies that ADMIN role can successfully execute privileged administrative operations",
                        f.getAffectedComponent()
                ));
            } else if ("SEC-TAINT-003".equals(ruleId)) {
                addIfAbsent(recommendations, seen, new TestRecommendation(
                        "com.impactgraph.SecurityIntegrationTest",
                        "accessOtherProject_forbidden",
                        "Verifies IDOR and project boundary enforcement across tenant projects",
                        f.getAffectedComponent()
                ));
                addIfAbsent(recommendations, seen, new TestRecommendation(
                        "com.impactgraph.SecurityIntegrationTest",
                        "revokeMembership_thenAccessDenied",
                        "Verifies access is revoked immediately when user project membership is removed",
                        f.getAffectedComponent()
                ));
            } else if ("SEC-DEPS-006".equals(ruleId)) {
                addIfAbsent(recommendations, seen, new TestRecommendation(
                        "com.impactgraph.SecurityIntegrationTest",
                        "* (Complete 35 Security Tests)",
                        "High blast radius: changes to shared security layer require full security regression test run",
                        f.getAffectedComponent()
                ));
            }
        }

        if (affectedComponents != null) {
            for (String comp : affectedComponents) {
                if (comp.contains("Graph") || comp.contains("Order") || comp.contains("Payment") || comp.contains("Checkout")) {
                    addIfAbsent(recommendations, seen, new TestRecommendation(
                            "com.impactgraph.service.GraphServiceTest",
                            "*",
                            "Verifies that dependency graph and cycle calculations remain deterministic",
                            comp
                    ));
                }
            }
        }

        return recommendations;
    }

    private void addIfAbsent(List<TestRecommendation> list, Set<String> seen, TestRecommendation rec) {
        String key = rec.testClass() + "#" + rec.testMethod();
        if (!seen.contains(key)) {
            seen.add(key);
            list.add(rec);
        }
    }
}
