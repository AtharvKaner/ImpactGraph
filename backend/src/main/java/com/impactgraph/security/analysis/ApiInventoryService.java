package com.impactgraph.security.analysis;

import com.impactgraph.dto.ApiInventoryItemDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service that builds a security-aware API inventory and tracks security drift across revisions.
 */
@Service
public class ApiInventoryService {

    private final JavaSourceAnalyzer sourceAnalyzer;

    // Known public endpoints defined in SecurityConfig
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/register",
            "/api/transparency/public",
            "/api/github/webhook"
    );

    @Autowired
    public ApiInventoryService(JavaSourceAnalyzer sourceAnalyzer) {
        this.sourceAnalyzer = sourceAnalyzer;
    }

    /**
     * Builds API Inventory from the current backend source tree.
     */
    public List<ApiInventoryItemDto> buildCurrentApiInventory() {
        Path backendSrc = Paths.get("src", "main", "java");
        if (!backendSrc.toFile().exists()) {
            backendSrc = Paths.get("backend", "src", "main", "java");
        }

        JavaSourceAnalyzer.SourceAnalysisResult result = sourceAnalyzer.analyzeDirectory(backendSrc);
        List<ApiInventoryItemDto> inventory = new ArrayList<>();

        for (JavaSourceAnalyzer.EndpointDefinition ep : result.endpoints()) {
            inventory.add(classifyEndpoint(ep));
        }

        // Sort by path then method
        inventory.sort(Comparator.comparing(ApiInventoryItemDto::path).thenComparing(ApiInventoryItemDto::method));
        return inventory;
    }

    /**
     * Classifies an endpoint definition into an inventory item.
     */
    public ApiInventoryItemDto classifyEndpoint(JavaSourceAnalyzer.EndpointDefinition ep) {
        String cleanPath = ep.fullPath().split("\\{")[0].replaceAll("/+$", "");
        if (cleanPath.isEmpty()) cleanPath = ep.fullPath();

        boolean isPublic = PUBLIC_PATHS.contains(ep.fullPath()) || PUBLIC_PATHS.contains(cleanPath);

        boolean isRoleBased = ep.fullPath().startsWith("/api/admin")
                || ep.fullPath().startsWith("/api/audit")
                || (ep.preAuthorizeExpr() != null && ep.preAuthorizeExpr().contains("hasRole"));

        String requiredRole = null;
        if (isRoleBased) {
            if (ep.preAuthorizeExpr() != null && ep.preAuthorizeExpr().contains("ADMIN")) {
                requiredRole = "ROLE_ADMIN";
            } else if (ep.fullPath().startsWith("/api/admin") || ep.fullPath().startsWith("/api/audit")) {
                requiredRole = "ROLE_ADMIN";
            } else {
                requiredRole = "ROLE_DEVELOPER";
            }
        }

        String authRequirement = isPublic ? "PUBLIC" : (isRoleBased ? "ROLE_BASED" : "AUTHENTICATED");
        String securitySource = isPublic ? "SecurityConfig.permitAll()" :
                (ep.preAuthorizeExpr() != null ? "@PreAuthorize(\"" + ep.preAuthorizeExpr() + "\")" : "SecurityConfig.authenticated()");

        List<String> sensitiveFields = detectSensitiveFields(ep);

        String riskLevel = "SAFE";
        if (isPublic && ("POST".equals(ep.httpMethod()) || "DELETE".equals(ep.httpMethod()) || "PUT".equals(ep.httpMethod()))
                && !ep.fullPath().startsWith("/api/auth") && !ep.fullPath().equals("/api/github/webhook")) {
            riskLevel = "CRITICAL";
        } else if (isPublic && !isExpectedPublicEndpoint(ep.fullPath())) {
            riskLevel = "HIGH";
        } else if (!isPublic && ("DELETE".equals(ep.httpMethod()) || "POST".equals(ep.httpMethod())) && !isRoleBased) {
            riskLevel = "MEDIUM";
        }

        return new ApiInventoryItemDto(
                ep.httpMethod(),
                ep.fullPath(),
                ep.controllerName(),
                ep.handlerMethod(),
                authRequirement,
                requiredRole,
                ep.preAuthorizeExpr() != null,
                isPublic,
                securitySource,
                sensitiveFields,
                "UNCHANGED",
                riskLevel
        );
    }

    private boolean isExpectedPublicEndpoint(String path) {
        return PUBLIC_PATHS.contains(path) || path.startsWith("/api/auth/") || path.equals("/api/transparency/public");
    }

    private List<String> detectSensitiveFields(JavaSourceAnalyzer.EndpointDefinition ep) {
        List<String> sensitive = new ArrayList<>();
        for (String param : ep.parameters()) {
            String lower = param.toLowerCase();
            if (lower.contains("password")) sensitive.add("password");
            if (lower.contains("secret")) sensitive.add("secret");
            if (lower.contains("token")) sensitive.add("token");
            if (lower.contains("credential")) sensitive.add("credential");
        }
        return sensitive;
    }

    /**
     * Compares base vs target endpoints to detect security drift.
     */
    public List<ApiInventoryItemDto> compareRevisions(List<ApiInventoryItemDto> base, List<ApiInventoryItemDto> target) {
        Map<String, ApiInventoryItemDto> baseMap = new HashMap<>();
        for (ApiInventoryItemDto b : base) {
            baseMap.put(b.method() + " " + b.path(), b);
        }

        List<ApiInventoryItemDto> compared = new ArrayList<>();
        for (ApiInventoryItemDto t : target) {
            String key = t.method() + " " + t.path();
            ApiInventoryItemDto baseItem = baseMap.get(key);

            if (baseItem == null) {
                // New endpoint
                compared.add(new ApiInventoryItemDto(
                        t.method(), t.path(), t.controllerName(), t.handlerMethod(),
                        t.authRequirement(), t.requiredRole(), t.hasPreAuthorize(), t.isPermitAll(),
                        t.securitySource(), t.sensitiveFields(), "NEW", t.riskLevel()
                ));
            } else if (!baseItem.authRequirement().equals(t.authRequirement()) || baseItem.isPermitAll() != t.isPermitAll()) {
                // Security regression / change
                String driftStatus = (!baseItem.isPermitAll() && t.isPermitAll()) ? "REGRESSED" : "MODIFIED";
                String risk = (!baseItem.isPermitAll() && t.isPermitAll()) ? "CRITICAL" : t.riskLevel();
                compared.add(new ApiInventoryItemDto(
                        t.method(), t.path(), t.controllerName(), t.handlerMethod(),
                        t.authRequirement(), t.requiredRole(), t.hasPreAuthorize(), t.isPermitAll(),
                        t.securitySource(), t.sensitiveFields(), driftStatus, risk
                ));
            } else {
                compared.add(t);
            }
        }

        return compared;
    }
}
