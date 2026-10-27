package com.impactgraph.security.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaSourceAnalyzerTest {

    private final JavaSourceAnalyzer analyzer = new JavaSourceAnalyzer();

    @Test
    void analyzeDirectory_extractsEndpointsAndAnnotations(@TempDir Path tempDir) throws IOException {
        String controllerCode = """
                package com.test;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.security.core.annotation.AuthenticationPrincipal;

                @RestController
                @RequestMapping("/api/test")
                public class SampleController {

                    @GetMapping("/hello")
                    public String hello() {
                        return "world";
                    }

                    @PreAuthorize("hasRole('ADMIN')")
                    @PostMapping("/secure")
                    public String securePost(@AuthenticationPrincipal Object user, @RequestBody String data) {
                        return "ok";
                    }
                }
                """;

        Files.writeString(tempDir.resolve("SampleController.java"), controllerCode);

        JavaSourceAnalyzer.SourceAnalysisResult result = analyzer.analyzeDirectory(tempDir);

        assertEquals(1, result.scannedFilesCount());
        List<JavaSourceAnalyzer.EndpointDefinition> endpoints = result.endpoints();
        assertEquals(2, endpoints.size());

        JavaSourceAnalyzer.EndpointDefinition getEp = endpoints.stream()
                .filter(e -> "GET".equals(e.httpMethod()))
                .findFirst().orElseThrow();
        assertEquals("/api/test/hello", getEp.fullPath());
        assertNull(getEp.preAuthorizeExpr());
        assertFalse(getEp.hasAuthenticationPrincipal());

        JavaSourceAnalyzer.EndpointDefinition postEp = endpoints.stream()
                .filter(e -> "POST".equals(e.httpMethod()))
                .findFirst().orElseThrow();
        assertEquals("/api/test/secure", postEp.fullPath());
        assertEquals("hasRole('ADMIN')", postEp.preAuthorizeExpr());
        assertTrue(postEp.hasAuthenticationPrincipal());
    }

    @Test
    void analyzeDirectory_handlesEmptyOrMissingDirectory() {
        JavaSourceAnalyzer.SourceAnalysisResult result = analyzer.analyzeDirectory(Path.of("non_existent_dir_xyz_123"));
        assertEquals(0, result.scannedFilesCount());
        assertTrue(result.endpoints().isEmpty());
    }
}
