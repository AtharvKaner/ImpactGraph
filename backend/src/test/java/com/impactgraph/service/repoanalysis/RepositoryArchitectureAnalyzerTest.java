package com.impactgraph.service.repoanalysis;

import com.impactgraph.dto.repoanalysis.DependencySummaryDto;
import com.impactgraph.dto.repoanalysis.ServiceSummaryDto;
import com.impactgraph.security.analysis.JavaSourceAnalyzer;
import com.impactgraph.security.analysis.SecurityRuleEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryArchitectureAnalyzerTest {

    private RepositoryArchitectureAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        JavaSourceAnalyzer sourceAnalyzer = new JavaSourceAnalyzer();
        SecurityRuleEngine ruleEngine = new SecurityRuleEngine();
        analyzer = new RepositoryArchitectureAnalyzer(sourceAnalyzer, ruleEngine);
    }

    @Test
    @DisplayName("Extracts services, dependencies, endpoints, and security findings from source fixture")
    void analyze_realisticJavaFixture_extractsArchitectureAndEvidence(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // 1. Controller component
        String orderController = """
                package com.example;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.beans.factory.annotation.Autowired;
                
                @RestController
                @RequestMapping("/api/orders")
                public class OrderController {
                    @Autowired
                    private OrderService orderService;
                    
                    @PostMapping("/checkout")
                    public String checkout() {
                        return orderService.processOrder();
                    }
                }
                """;
        Files.writeString(srcDir.resolve("OrderController.java"), orderController);

        // 2. Service component with injection
        String orderService = """
                package com.example;
                import org.springframework.stereotype.Service;
                
                @Service
                public class OrderService {
                    private final PaymentClient paymentClient;
                    
                    public OrderService(PaymentClient paymentClient) {
                        this.paymentClient = paymentClient;
                    }
                    
                    public String processOrder() {
                        return paymentClient.charge();
                    }
                }
                """;
        Files.writeString(srcDir.resolve("OrderService.java"), orderService);

        // 3. Client component with FeignClient and a hardcoded token for security test
        String paymentClient = """
                package com.example;
                import org.springframework.cloud.openfeign.FeignClient;
                import org.springframework.stereotype.Component;
                
                @Component
                @FeignClient(name = "payment-gateway")
                public class PaymentClient {
                    private String apiKey = "secret-live-token-12345";
                    
                    public String charge() {
                        return "CHARGED";
                    }
                }
                """;
        Files.writeString(srcDir.resolve("PaymentClient.java"), paymentClient);

        // Analyze fixture
        RepositoryArchitectureAnalyzer.ArchitectureAnalysisResult result = analyzer.analyze(tempDir);

        // Verify services
        assertThat(result.services()).extracting(ServiceSummaryDto::name)
                .contains("OrderController", "OrderService", "PaymentClient");

        ServiceSummaryDto controllerDto = result.services().stream()
                .filter(s -> s.name().equals("OrderController")).findFirst().orElseThrow();
        assertThat(controllerDto.tier()).contains("Gateway");
        assertThat(controllerDto.apiCount()).isEqualTo(1);
        assertThat(controllerDto.apis()).contains("POST /api/orders/checkout");

        // Verify dependencies with evidence
        assertThat(result.dependencies()).isNotEmpty();
        List<String> edgeArrows = result.dependencies().stream()
                .map(d -> d.sourceService() + " -> " + d.targetService())
                .toList();

        assertThat(edgeArrows).contains("OrderController -> OrderService");
        assertThat(edgeArrows).contains("OrderService -> PaymentClient");
        assertThat(edgeArrows).contains("PaymentClient -> payment-gateway");

        DependencySummaryDto controllerDep = result.dependencies().stream()
                .filter(d -> d.sourceService().equals("OrderController") && d.targetService().equals("OrderService"))
                .findFirst().orElseThrow();
        assertThat(controllerDep.evidenceSnippet()).contains("OrderService");
        assertThat(controllerDep.lineNumber()).isGreaterThan(0);

        // Verify security findings
        assertThat(result.findings()).isNotEmpty();
        assertThat(result.findings()).extracting("ruleId").contains("SEC-DATA-004");
    }
}
