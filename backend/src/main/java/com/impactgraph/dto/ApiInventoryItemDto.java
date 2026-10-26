package com.impactgraph.dto;

import java.util.List;

/**
 * DTO representing an API endpoint in the security-aware API inventory.
 */
public record ApiInventoryItemDto(
        String method,
        String path,
        String controllerName,
        String handlerMethod,
        String authRequirement, // "PUBLIC", "AUTHENTICATED", "ROLE_BASED"
        String requiredRole,    // e.g. "ROLE_ADMIN", "ROLE_DEVELOPER", null
        boolean hasPreAuthorize,
        boolean isPermitAll,
        String securitySource,  // "SecurityConfig URL Matcher", "@PreAuthorize", etc.
        List<String> sensitiveFields,
        String driftStatus,     // "UNCHANGED", "NEW", "REMOVED", "MODIFIED", "REGRESSED"
        String riskLevel        // "CRITICAL", "HIGH", "MEDIUM", "LOW", "SAFE"
) {}
