package com.impactgraph.security.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic Java source code analyzer that extracts controllers,
 * request mappings, security annotations, service method calls, and data flows.
 */
@Component
public class JavaSourceAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceAnalyzer.class);

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public\\s+)?(?:abstract\\s+)?class\\s+([A-Za-z0-9_]+)", Pattern.DOTALL);

    private static final Pattern REQUEST_MAPPING_CLASS = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"\\)", Pattern.DOTALL);

    private static final Pattern PRE_AUTHORIZE_PATTERN = Pattern.compile(
            "@PreAuthorize\\s*\\(\\s*\"([^\"]+)\"\\)", Pattern.DOTALL);

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping(?:\\s*\\(\\s*(?:(?:value\\s*=\\s*)?\"([^\"]*)\"|path\\s*=\\s*\"([^\"]*)\")?\\s*\\))?" +
            "[^;{]*?\\s+([A-Za-z0-9_<>,\\[\\]\\s]+)\\s+([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)",
            Pattern.DOTALL);

    public record EndpointDefinition(
            String controllerName,
            String handlerMethod,
            String httpMethod,
            String fullPath,
            String preAuthorizeExpr,
            boolean hasAuthenticationPrincipal,
            List<String> parameters,
            String sourceFilePath,
            int lineNumber,
            List<String> invokedMethods,
            List<String> referencedEntities
    ) {}

    public record SourceAnalysisResult(
            List<EndpointDefinition> endpoints,
            Map<String, List<String>> serviceCallHierarchy,
            Map<String, String> securityConfigurations,
            int scannedFilesCount
    ) {}

    /**
     * Scans a root source directory (e.g. backend/src/main/java) and builds an inventory of endpoints.
     */
    public SourceAnalysisResult analyzeDirectory(Path rootDir) {
        List<EndpointDefinition> endpoints = new ArrayList<>();
        Map<String, List<String>> callHierarchy = new HashMap<>();
        Map<String, String> securityConfigs = new HashMap<>();
        int fileCount = 0;

        if (rootDir == null || !Files.exists(rootDir)) {
            return new SourceAnalysisResult(endpoints, callHierarchy, securityConfigs, 0);
        }

        try (var stream = Files.walk(rootDir)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            fileCount = javaFiles.size();

            for (Path file : javaFiles) {
                try {
                    String content = Files.readString(file);
                    analyzeFile(file, content, endpoints, callHierarchy, securityConfigs);
                } catch (IOException e) {
                    log.warn("Failed to read Java file during security analysis: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to walk directory for source analysis: {}", rootDir, e);
        }

        return new SourceAnalysisResult(endpoints, callHierarchy, securityConfigs, fileCount);
    }

    /**
     * Scans a single Java source file and extracts its endpoint definitions and configurations.
     */
    public SourceAnalysisResult analyzeSingleFile(Path file) {
        List<EndpointDefinition> endpoints = new ArrayList<>();
        Map<String, List<String>> callHierarchy = new HashMap<>();
        Map<String, String> securityConfigs = new HashMap<>();

        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            return new SourceAnalysisResult(endpoints, callHierarchy, securityConfigs, 0);
        }

        try {
            String content = Files.readString(file);
            analyzeFile(file, content, endpoints, callHierarchy, securityConfigs);
            return new SourceAnalysisResult(endpoints, callHierarchy, securityConfigs, 1);
        } catch (IOException e) {
            log.warn("Failed to read single Java file during analysis: {}", file, e);
            return new SourceAnalysisResult(endpoints, callHierarchy, securityConfigs, 0);
        }
    }

    private void analyzeFile(Path file, String content,
                            List<EndpointDefinition> endpoints,
                            Map<String, List<String>> callHierarchy,
                            Map<String, String> securityConfigs) {
        String fileName = file.getFileName().toString();

        if (fileName.contains("SecurityConfig")) {
            securityConfigs.put("SecurityConfig", content);
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (!classMatcher.find()) return;
        String className = classMatcher.group(1);

        boolean isController = content.contains("@RestController") || content.contains("@Controller");
        if (!isController) {
            // Track service-level calls
            List<String> downstreamCalls = extractMethodCalls(content);
            callHierarchy.put(className, downstreamCalls);
            return;
        }

        // Extract class-level @RequestMapping
        String basePath = "";
        Matcher baseMappingMatcher = REQUEST_MAPPING_CLASS.matcher(content);
        if (baseMappingMatcher.find()) {
            basePath = baseMappingMatcher.group(1).trim();
            if (!basePath.startsWith("/")) basePath = "/" + basePath;
            if (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
        }

        // Class-level @PreAuthorize (only before opening brace of the class)
        String classPreAuthorize = null;
        int classOpenBrace = content.indexOf('{', classMatcher.end());
        String classHeader = classOpenBrace > 0 ? content.substring(0, classOpenBrace) : content.substring(0, Math.min(content.length(), 500));
        Matcher classPreAuthMatcher = PRE_AUTHORIZE_PATTERN.matcher(classHeader);
        if (classPreAuthMatcher.find()) {
            classPreAuthorize = classPreAuthMatcher.group(1);
        }

        // Extract methods
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            String httpMethod = methodMatcher.group(1).toUpperCase();
            String pathPart = methodMatcher.group(2);
            if (pathPart == null) pathPart = methodMatcher.group(3);
            if (pathPart == null) pathPart = "";
            if (!pathPart.isEmpty() && !pathPart.startsWith("/")) pathPart = "/" + pathPart;

            String fullPath = basePath + pathPart;
            if (fullPath.isEmpty()) fullPath = "/";

            String methodName = methodMatcher.group(5);
            String paramsRaw = methodMatcher.group(6);

            boolean hasAuthPrincipal = paramsRaw != null && (
                    paramsRaw.contains("@AuthenticationPrincipal") ||
                    paramsRaw.contains("JwtAuthenticatedUser")
            );

            // Method-level @PreAuthorize (before method definition)
            int methodStart = methodMatcher.start();
            int lookback = Math.max(0, methodStart - 200);
            String prefix = content.substring(lookback, methodStart);

            String methodPreAuth = classPreAuthorize;
            Matcher mAuth = PRE_AUTHORIZE_PATTERN.matcher(prefix);
            if (mAuth.find()) {
                methodPreAuth = mAuth.group(1);
            }

            int lineNum = countLines(content.substring(0, methodStart));

            // Extract method body calls
            int bodyStart = content.indexOf('{', methodMatcher.end());
            int bodyEnd = findMatchingBrace(content, bodyStart);
            List<String> invoked = List.of();
            if (bodyStart > 0 && bodyEnd > bodyStart) {
                String methodBody = content.substring(bodyStart, bodyEnd);
                invoked = extractMethodCalls(methodBody);
            }

            endpoints.add(new EndpointDefinition(
                    className,
                    methodName,
                    httpMethod,
                    fullPath,
                    methodPreAuth,
                    hasAuthPrincipal,
                    Arrays.stream(paramsRaw != null ? paramsRaw.split(",") : new String[0])
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList()),
                    file.toString(),
                    lineNum,
                    invoked,
                    extractEntityReferences(content)
            ));
        }
    }

    private List<String> extractMethodCalls(String body) {
        Set<String> calls = new LinkedHashSet<>();
        Pattern callPattern = Pattern.compile("([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\s*\\(");
        Matcher m = callPattern.matcher(body);
        while (m.find()) {
            String receiver = m.group(1);
            String method = m.group(2);
            if (!receiver.equals("this") && !receiver.equals("log") && !receiver.equals("ResponseEntity")) {
                calls.add(receiver + "#" + method);
            }
        }
        return new ArrayList<>(calls);
    }

    private List<String> extractEntityReferences(String content) {
        Set<String> entities = new LinkedHashSet<>();
        Pattern entityPattern = Pattern.compile("\\b([A-Z][A-Za-z0-9]+Repository)\\b");
        Matcher m = entityPattern.matcher(content);
        while (m.find()) {
            entities.add(m.group(1));
        }
        return new ArrayList<>(entities);
    }

    private int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private int findMatchingBrace(String text, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= text.length()) return -1;
        int depth = 0;
        for (int i = openBraceIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
