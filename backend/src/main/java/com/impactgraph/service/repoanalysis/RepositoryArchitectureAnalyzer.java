package com.impactgraph.service.repoanalysis;

import com.impactgraph.dto.repoanalysis.DependencySummaryDto;
import com.impactgraph.dto.repoanalysis.ServiceSummaryDto;
import com.impactgraph.entity.Confidence;
import com.impactgraph.entity.FindingStatus;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.entity.Severity;
import com.impactgraph.security.analysis.JavaSourceAnalyzer;
import com.impactgraph.security.analysis.SecurityRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts microservices, components, API endpoints, and directed dependencies
 * from real repository source code and build descriptors.
 * Supports Java (Spring Boot), JavaScript/TypeScript (Node/Express/React), and Python.
 */
@Service
public class RepositoryArchitectureAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RepositoryArchitectureAnalyzer.class);

    private final JavaSourceAnalyzer javaSourceAnalyzer;
    private final SecurityRuleEngine securityRuleEngine;

    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?(?:class|interface)\\s+([A-Za-z0-9_]+)", Pattern.DOTALL);

    private static final Pattern JAVA_IMPORT_PATTERN = Pattern.compile(
            "import\\s+([A-Za-z0-9_.]+);");

    private static final Pattern JS_IMPORT_PATTERN = Pattern.compile(
            "(?:import\\s+.*\\s+from\\s+['\"]([^'\"]+)['\"]|require\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\))");

    private static final Pattern JS_ROUTE_PATTERN = Pattern.compile(
            "(?:app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    private static final Pattern FEIGN_CLIENT_PATTERN = Pattern.compile(
            "@FeignClient\\s*\\(\\s*(?:value\\s*=\\s*|name\\s*=\\s*)?\"([^\"]+)\"\\)");

    @Autowired
    public RepositoryArchitectureAnalyzer(JavaSourceAnalyzer javaSourceAnalyzer,
                                          SecurityRuleEngine securityRuleEngine) {
        this.javaSourceAnalyzer = javaSourceAnalyzer;
        this.securityRuleEngine = securityRuleEngine;
    }

    public record ArchitectureAnalysisResult(
            List<ServiceSummaryDto> services,
            List<DependencySummaryDto> dependencies,
            List<JavaSourceAnalyzer.EndpointDefinition> endpoints,
            List<SecurityFinding> findings,
            int scannedFilesCount,
            List<String> detectedLanguages
    ) {}

    /**
     * Inspects a cloned or local repository directory and extracts architecture and dependencies.
     */
    public ArchitectureAnalysisResult analyze(Path rootDir) {
        if (rootDir == null || !Files.exists(rootDir)) {
            return new ArchitectureAnalysisResult(List.of(), List.of(), List.of(), List.of(), 0, List.of());
        }

        Map<String, ServiceSummaryDto> serviceMap = new LinkedHashMap<>();
        List<DependencySummaryDto> dependencyList = new ArrayList<>();
        List<JavaSourceAnalyzer.EndpointDefinition> allEndpoints = new ArrayList<>();
        List<SecurityFinding> findings = new ArrayList<>();
        Set<String> languages = new TreeSet<>();
        int fileCount = 0;

        try {
            // 1. Walk directory and catalog source files
            List<Path> allFiles = new ArrayList<>();
            try (var stream = Files.walk(rootDir)) {
                allFiles = stream.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains(".git") && !p.toString().contains("node_modules") && !p.toString().contains("target") && !p.toString().contains("build"))
                        .toList();
            }

            fileCount = allFiles.size();

            // Language detection
            for (Path file : allFiles) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".java")) languages.add("Java");
                else if (name.endsWith(".js") || name.endsWith(".jsx")) languages.add("JavaScript");
                else if (name.endsWith(".ts") || name.endsWith(".tsx")) languages.add("TypeScript");
                else if (name.endsWith(".py")) languages.add("Python");
                else if (name.equals("pom.xml")) languages.add("Maven");
                else if (name.equals("package.json")) languages.add("npm");
            }

            // 2. Discover Java / Spring Boot components
            discoverJavaComponents(rootDir, allFiles, serviceMap, allEndpoints, findings);

            // 3. Discover JavaScript / Node components if any
            discoverJsComponents(rootDir, allFiles, serviceMap, allEndpoints);

            // 4. Extract dependencies between discovered services with evidence
            extractDependencies(allFiles, serviceMap, dependencyList);

            // 5. If no code-level services were detected, fallback to module directory partitioning
            if (serviceMap.isEmpty()) {
                discoverDirectoryComponents(rootDir, serviceMap, dependencyList);
            }

            log.info("Architecture analysis complete for {}: {} services, {} dependencies, {} endpoints, {} security findings",
                    rootDir, serviceMap.size(), dependencyList.size(), allEndpoints.size(), findings.size());

        } catch (Exception e) {
            log.error("Failed to analyze repository architecture in {}", rootDir, e);
        }

        return new ArchitectureAnalysisResult(
                new ArrayList<>(serviceMap.values()),
                dependencyList,
                allEndpoints,
                findings,
                fileCount,
                new ArrayList<>(languages)
        );
    }

    private void discoverJavaComponents(Path rootDir, List<Path> files,
                                        Map<String, ServiceSummaryDto> serviceMap,
                                        List<JavaSourceAnalyzer.EndpointDefinition> allEndpoints,
                                        List<SecurityFinding> findings) {
        for (Path file : files) {
            if (!file.toString().endsWith(".java")) continue;

            try {
                String content = Files.readString(file);
                Matcher classMatcher = JAVA_CLASS_PATTERN.matcher(content);
                if (!classMatcher.find()) continue;

                String className = classMatcher.group(1);
                String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
                int classLine = getLineNumber(content, classMatcher.start());

                boolean isController = content.contains("@RestController") || content.contains("@Controller");
                boolean isService = content.contains("@Service") || content.contains("@Component") || className.endsWith("Service");
                boolean isRepository = content.contains("@Repository") || className.endsWith("Repository");
                boolean isSecurity = className.contains("Security") || className.contains("Auth");

                if (isController || isService || isRepository || isSecurity) {
                    String tier = isController ? "Client / Gateway Tier" : (isRepository ? "Infrastructure Tier" : "Domain Tier");
                    String type = isController ? "REST Controller" : (isRepository ? "Data Access" : "Core Service");

                    List<String> apis = new ArrayList<>();
                    if (isController) {
                        JavaSourceAnalyzer.SourceAnalysisResult res = javaSourceAnalyzer.analyzeSingleFile(file);
                        allEndpoints.addAll(res.endpoints());
                        for (JavaSourceAnalyzer.EndpointDefinition ep : res.endpoints()) {
                            apis.add(ep.httpMethod() + " " + ep.fullPath());
                        }
                    }

                    ServiceSummaryDto svc = new ServiceSummaryDto(
                            className,
                            type,
                            tier,
                            relativePath,
                            classLine,
                            apis.size(),
                            0,
                            apis
                    );
                    serviceMap.put(className, svc);

                    // Run security analysis on this file
                    evaluateSecurityRules(file, relativePath, content, className, isController, findings);
                }
            } catch (Exception e) {
                log.warn("Failed to parse Java component {}: {}", file, e.getMessage());
            }
        }
    }

    private void discoverJsComponents(Path rootDir, List<Path> files,
                                      Map<String, ServiceSummaryDto> serviceMap,
                                      List<JavaSourceAnalyzer.EndpointDefinition> allEndpoints) {
        for (Path file : files) {
            String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!fileName.endsWith(".js") && !fileName.endsWith(".ts") && !fileName.endsWith(".jsx") && !fileName.endsWith(".tsx")) continue;
            if (fileName.contains(".test.") || fileName.contains(".spec.")) continue;

            try {
                String content = Files.readString(file);
                String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
                String baseName = fileName.replaceAll("\\.[^.]+$", "");

                // Check for express routes
                Matcher routeMatcher = JS_ROUTE_PATTERN.matcher(content);
                List<String> routes = new ArrayList<>();
                while (routeMatcher.find()) {
                    String method = routeMatcher.group(1).toUpperCase(Locale.ROOT);
                    String path = routeMatcher.group(2);
                    routes.add(method + " " + path);
                }

                if (!routes.isEmpty() || baseName.toLowerCase(Locale.ROOT).contains("service") || baseName.toLowerCase(Locale.ROOT).contains("api")) {
                    String tier = !routes.isEmpty() ? "API Gateway Tier" : "Application Tier";
                    String type = !routes.isEmpty() ? "Route Handler" : "JavaScript Module";

                    ServiceSummaryDto svc = new ServiceSummaryDto(
                            baseName,
                            type,
                            tier,
                            relativePath,
                            1,
                            routes.size(),
                            0,
                            routes
                    );
                    serviceMap.putIfAbsent(baseName, svc);
                }
            } catch (Exception ignored) {}
        }
    }

    private void extractDependencies(List<Path> files,
                                     Map<String, ServiceSummaryDto> serviceMap,
                                     List<DependencySummaryDto> dependencyList) {
        Set<String> seenEdges = new HashSet<>();

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String matchedService = null;
            for (String svcName : serviceMap.keySet()) {
                if (fileName.startsWith(svcName)) {
                    matchedService = svcName;
                    break;
                }
            }
            if (matchedService == null) continue;

            try {
                String content = Files.readString(file);
                String relativePath = file.toString().replace('\\', '/');

                // 1. Feign Client dependencies
                Matcher feignMatcher = FEIGN_CLIENT_PATTERN.matcher(content);
                while (feignMatcher.find()) {
                    String targetName = feignMatcher.group(1).trim();
                    addEdge(matchedService, targetName, "FEIGN_CLIENT", relativePath,
                            getLineNumber(content, feignMatcher.start()),
                            feignMatcher.group(0), dependencyList, seenEdges);
                }

                // 2. Java Imports / references
                Matcher importMatcher = JAVA_IMPORT_PATTERN.matcher(content);
                while (importMatcher.find()) {
                    String importedFqcn = importMatcher.group(1);
                    for (String otherSvc : serviceMap.keySet()) {
                        if (otherSvc.equals(matchedService)) continue;
                        if (importedFqcn.endsWith("." + otherSvc) || importedFqcn.equals(otherSvc)) {
                            addEdge(matchedService, otherSvc, "JAVA_IMPORT", relativePath,
                                    getLineNumber(content, importMatcher.start()),
                                    importMatcher.group(0), dependencyList, seenEdges);
                        }
                    }
                }

                // 3. Injected fields or method parameters (e.g. "private final TargetService ...")
                for (String otherSvc : serviceMap.keySet()) {
                    if (otherSvc.equals(matchedService)) continue;
                    Pattern fieldPattern = Pattern.compile("(?:private|protected|public)\\s+(?:final\\s+)?(" + otherSvc + ")\\s+([a-zA-Z0-9_]+);");
                    Matcher fieldMatcher = fieldPattern.matcher(content);
                    if (fieldMatcher.find()) {
                        addEdge(matchedService, otherSvc, "INJECTION", relativePath,
                                getLineNumber(content, fieldMatcher.start()),
                                fieldMatcher.group(0), dependencyList, seenEdges);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void discoverDirectoryComponents(Path rootDir,
                                             Map<String, ServiceSummaryDto> serviceMap,
                                             List<DependencySummaryDto> dependencyList) {
        try (var stream = Files.list(rootDir)) {
            List<Path> subDirs = stream.filter(Files::isDirectory)
                    .filter(d -> !d.getFileName().toString().startsWith(".") && !d.getFileName().toString().equals("target") && !d.getFileName().toString().equals("build"))
                    .toList();

            for (Path dir : subDirs) {
                String name = dir.getFileName().toString();
                String rel = rootDir.relativize(dir).toString().replace('\\', '/');
                serviceMap.putIfAbsent(name, new ServiceSummaryDto(
                        name,
                        "Module Component",
                        "Application Tier",
                        rel,
                        1,
                        0,
                        0,
                        List.of()
                ));
            }
        } catch (Exception ignored) {}
    }

    private void addEdge(String source, String target, String type, String filePath, int line, String snippet,
                         List<DependencySummaryDto> list, Set<String> seen) {
        if (source == null || target == null || source.equalsIgnoreCase(target)) return;
        String key = source + "->" + target;
        if (seen.add(key)) {
            list.add(new DependencySummaryDto(
                    source,
                    target,
                    type,
                    filePath,
                    line,
                    snippet != null ? snippet.trim() : ""
            ));
        }
    }

    private void evaluateSecurityRules(Path file, String relativePath, String content,
                                       String className, boolean isController,
                                       List<SecurityFinding> findings) {
        // Detect hardcoded secret or token
        if (content.contains("secret = \"") || content.contains("password = \"") || content.contains("apiKey = \"")) {
            SecurityFinding f = new SecurityFinding();
            f.setRuleId("SEC-DATA-004");
            f.setAffectedComponent(className);
            f.setAffectedEndpoint("/");
            f.setSeverity(Severity.CRITICAL);
            f.setStatus(FindingStatus.OPEN);
            f.setTitle("Hardcoded Credential Detected in " + className);
            f.setDescription("Hardcoded secret/credential literal identified in source file: " + relativePath);
            f.setSuggestedRemediation("Move credentials into environment variables or a secure secret store (e.g., Vault).");
            f.setConfidence(Confidence.HIGH);
            f.setStartLine(findLine(content, "password = \""));
            findings.add(f);
        }

        // Detect missing authz on sensitive mutation routes
        if (isController && (content.contains("@DeleteMapping") || content.contains("@PostMapping")) &&
            !content.contains("@PreAuthorize") && !content.contains("hasRole") && !content.contains("SecurityConfig")) {
            SecurityFinding f = new SecurityFinding();
            f.setRuleId("SEC-AUTHZ-002");
            f.setAffectedComponent(className);
            f.setAffectedEndpoint("/api/" + className.toLowerCase(Locale.ROOT).replace("controller", ""));
            f.setSeverity(Severity.HIGH);
            f.setStatus(FindingStatus.OPEN);
            f.setTitle("Missing Authorization Check on State-Changing API in " + className);
            f.setDescription("Controller exposes mutation endpoints without role or authority verification.");
            f.setSuggestedRemediation("Apply @PreAuthorize(\"hasRole('ADMIN')\") or enforce permission checks in SecurityFilterChain.");
            f.setConfidence(Confidence.MEDIUM);
            f.setStartLine(1);
            findings.add(f);
        }

        // Permissive CORS check
        if (content.contains("allowedOrigins = \"*\"") || content.contains("addAllowedOrigin(\"*\")")) {
            SecurityFinding f = new SecurityFinding();
            f.setRuleId("SEC-CONF-005");
            f.setAffectedComponent(className);
            f.setAffectedEndpoint("/*");
            f.setSeverity(Severity.HIGH);
            f.setStatus(FindingStatus.OPEN);
            f.setTitle("Permissive Wildcard CORS Configuration in " + className);
            f.setDescription("Access-Control-Allow-Origin is configured with wildcard '*' allowing untrusted cross-origin requests.");
            f.setSuggestedRemediation("Restrict allowed origins to trusted frontend domains.");
            f.setConfidence(Confidence.HIGH);
            f.setStartLine(findLine(content, "*"));
            findings.add(f);
        }
    }

    private int getLineNumber(String content, int charIndex) {
        if (content == null || charIndex <= 0) return 1;
        int line = 1;
        for (int i = 0; i < Math.min(charIndex, content.length()); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private int findLine(String content, String sub) {
        int idx = content.indexOf(sub);
        return idx >= 0 ? getLineNumber(content, idx) : 1;
    }
}
