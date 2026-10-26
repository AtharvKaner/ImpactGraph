package com.impactgraph.service;

import com.impactgraph.entity.*;
import com.impactgraph.entity.Service;
import com.impactgraph.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Deterministic Demo Project Seeder for ImpactGraph.
 * Seeds a production-like 8-service e-commerce cloud platform with realistic
 * client, application, domain, and infrastructure tiers, 20 APIs, 10 dependencies,
 * 5 security findings tied to blast-radius components, and a baseline analysis run.
 */
@Component
@Order(10)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "demo.seeder.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PROJECT_NAME = "[Demo] E-Commerce Platform";
    public static final String DEMO_USER_EMAIL = "demo@impactgraph.io";
    public static final String DEMO_USER_PASSWORD = "DemoPassword123!";

    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final ApiRepository apiRepository;
    private final DependencyRepository dependencyRepository;
    private final SecurityFindingRepository findingRepository;
    private final AnalysisRunRepository runRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(ProjectRepository projectRepository,
                          ServiceRepository serviceRepository,
                          ApiRepository apiRepository,
                          DependencyRepository dependencyRepository,
                          SecurityFindingRepository findingRepository,
                          AnalysisRunRepository runRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.apiRepository = apiRepository;
        this.dependencyRepository = dependencyRepository;
        this.findingRepository = findingRepository;
        this.runRepository = runRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureDemoUser();
        seedDemoProjectIfMissing();
    }

    @Transactional
    public void ensureDemoUser() {
        if (!userRepository.existsByEmail(DEMO_USER_EMAIL)) {
            AppUser demoUser = new AppUser();
            demoUser.setEmail(DEMO_USER_EMAIL);
            demoUser.setDisplayName("Demo Engineer");
            demoUser.setPasswordHash(passwordEncoder.encode(DEMO_USER_PASSWORD));
            demoUser.setRole(Role.DEVELOPER);
            userRepository.save(demoUser);
            log.info("Demo user account created: {}", DEMO_USER_EMAIL);
        }
    }

    @Transactional
    public Project seedDemoProjectIfMissing() {
        Optional<Project> existing = projectRepository.findByName(DEMO_PROJECT_NAME);
        if (existing.isPresent()) {
            return existing.get();
        }

        log.info("Seeding deterministic Demo Project '{}' with realistic multi-service topology...", DEMO_PROJECT_NAME);
        Project project = new Project();
        project.setName(DEMO_PROJECT_NAME);
        project.setDescription("Realistic 8-service e-commerce cloud platform with client, application, domain, and infrastructure tiers for change-impact and security regression simulation.");
        project = projectRepository.save(project);

        // 1. Create Microservices
        Service storefront = createService("Storefront Web", "User-facing web application portal and edge gateway (Client Tier)", project);
        Service checkout = createService("Checkout Service", "Checkout orchestration and multi-step cart engine (Application Tier)", project);
        Service auth = createService("Auth Service", "Identity provider, JWT token issuance, and credential verification (Infrastructure Tier)", project);
        Service order = createService("Order Service", "Order lifecycle management, status persistence, and cart validation (Domain Tier)", project);
        Service payment = createService("Payment Service", "Payment gateway settlement, refund processing, and audit hooks (Domain Tier)", project);
        Service inventory = createService("Inventory Service", "Real-time SKU stock levels and warehouse reservation (Domain Tier)", project);
        Service notification = createService("Notification Service", "Customer dispatch service for order receipts and security alerts (Integration Tier)", project);
        Service audit = createService("Audit Logger", "Immutable compliance ledger and CNS security audit trail (Security Tier)", project);

        // 2. Create Endpoints / APIs
        createApi("Storefront UI", "GET", "/", "Renders customer storefront landing page", storefront);
        createApi("Catalog Search", "GET", "/products", "Fetches active product catalog", storefront);
        createApi("Submit Checkout", "POST", "/cart/checkout", "Submits cart to checkout orchestrator", storefront);

        createApi("Start Checkout", "POST", "/checkout/start", "Initiates multi-step checkout workflow", checkout);
        createApi("Confirm Order", "POST", "/checkout/confirm", "Commits order and executes payment charge", checkout);

        createApi("User Login", "POST", "/auth/login", "Authenticates user and returns JWT", auth);
        createApi("Verify Token", "POST", "/auth/verify", "Validates JWT signature and claims", auth);
        createApi("Token Refresh", "POST", "/auth/refresh", "Issues new token using refresh credential", auth);

        createApi("Create Order", "POST", "/orders", "Places a new customer order", order);
        createApi("Get Order", "GET", "/orders/{id}", "Fetches order details by ID", order);
        createApi("Update Order Status", "PUT", "/orders/{id}/status", "Updates order lifecycle status", order);

        createApi("Charge Payment", "POST", "/payments/charge", "Executes credit card charge via payment gateway", payment);
        createApi("Refund Payment", "POST", "/payments/refund", "Processes refund against settlement transaction", payment);

        createApi("Get Stock Level", "GET", "/inventory/{sku}", "Checks available warehouse inventory", inventory);
        createApi("Reserve Stock", "POST", "/inventory/reserve", "Holds stock during checkout session", inventory);
        createApi("Release Stock", "POST", "/inventory/release", "Releases held stock upon checkout cancellation", inventory);

        createApi("Send Notification", "POST", "/notifications/send", "Sends email/SMS dispatch notification", notification);
        createApi("Get Notification Status", "GET", "/notifications/{id}", "Checks dispatch status", notification);

        createApi("Log Audit Event", "POST", "/audit/events", "Appends immutable security audit event", audit);
        createApi("Query Audit Events", "GET", "/audit/events", "Retrieves audit log entries", audit);

        // 3. Create Dependencies (Convention: Source depends on Target)
        createDep(storefront, checkout, "REST", "Storefront calls Checkout Service to initiate order workflow");
        createDep(storefront, auth, "REST", "Storefront calls Auth Service to authenticate user sessions");
        createDep(checkout, order, "REST", "Checkout orchestrator creates orders in Order Service");
        createDep(checkout, payment, "REST", "Checkout orchestrator charges payments in Payment Service");
        createDep(order, payment, "REST", "Order Service queries Payment Service for transaction status");
        createDep(order, inventory, "REST", "Order Service checks stock availability in Inventory Service");
        createDep(order, audit, "EVENT", "Order Service dispatches order state transitions to Audit Logger");
        createDep(payment, notification, "EVENT", "Payment Service triggers receipt notifications upon charge");
        createDep(payment, audit, "EVENT", "Payment Service records financial transactions in Audit Logger");
        createDep(auth, audit, "EVENT", "Auth Service logs authentication successes and failures");

        // 4. Create Project-Scoped Security Findings
        seedFinding("SEC-AUTHZ-002", "Payment Refund Endpoint Missing Authorization Enforcement",
                "Payment refund handler accepts refund requests without verifying @PreAuthorize(\"hasRole('ADMIN')\").",
                Severity.HIGH, Confidence.CONFIRMED, FindingStatus.OPEN,
                "backend/src/main/java/com/impactgraph/controller/PaymentController.java",
                42, 50, "POST /payments/refund", "POST", "Payment Service",
                "PaymentController#refundPayment missing @PreAuthorize or role check",
                "Direct exposure of refund endpoint allows unprivileged users to trigger unauthorized refunds. Because Order Service and Checkout Service depend on Payment Service, downstream trust is violated.",
                "Add @PreAuthorize(\"hasRole('ADMIN')\") to the refund endpoint and enforce tenant project access.",
                "com.impactgraph.SecurityIntegrationTest#refundUnauthorized_forbidden",
                project.getId());

        seedFinding("SEC-TAINT-003", "Order Query Endpoint Missing Project Membership Verification (IDOR)",
                "Order retrieval endpoint queries database by path ID without verifying caller belongs to project.",
                Severity.HIGH, Confidence.CONFIRMED, FindingStatus.OPEN,
                "backend/src/main/java/com/impactgraph/controller/ProjectController.java",
                54, 62, "GET /api/projects/{projectId}/services", "GET", "Order Service",
                "ProjectController#getServicesForProject -> missing enforceProjectAccess",
                "Authenticated users from Organization A can inspect order services and topologies of Organization B by manipulating the projectId parameter.",
                "Add projectService.enforceProjectAccess(user, projectId) before retrieving resources.",
                "com.impactgraph.SecurityIntegrationTest#accessOtherProject_forbidden",
                project.getId());

        seedFinding("SEC-CONF-005", "Wildcard CORS Configuration on Sensitive Storefront Gateway",
                "Storefront Web Gateway configures CorsConfiguration.addAllowedOrigin(\"*\") alongside allowCredentials=true.",
                Severity.MEDIUM, Confidence.HIGH, FindingStatus.OPEN,
                "backend/src/main/java/com/impactgraph/config/WebConfig.java",
                25, 35, "ALL /api/**", "ALL", "Storefront Web",
                "WebConfig#corsFilter specifies wildcard allowed origin",
                "Permitting all origins allows unauthorized third-party domains to initiate authenticated requests via ambient browser credentials.",
                "Restrict allowed origins to trusted domains via environment variable CORS_ALLOWED_ORIGINS.",
                "com.impactgraph.SecurityIntegrationTest#corsWildcard_rejected",
                project.getId());

        seedFinding("SEC-AUTH-001", "Notification Dispatch Endpoint Explicitly Permits Unauthenticated Access",
                "SecurityConfig explicitly marks /notifications/send as permitAll() without authentication token check.",
                Severity.HIGH, Confidence.CONFIRMED, FindingStatus.OPEN,
                "backend/src/main/java/com/impactgraph/security/SecurityConfig.java",
                70, 75, "POST /notifications/send", "POST", "Notification Service",
                "SecurityConfig.java -> .requestMatchers(\"/notifications/send\").permitAll()",
                "Anyone on the internal network can dispatch fraudulent customer notices without valid credentials.",
                "Remove permitAll() and require authenticated bearer token with hasRole('SERVICE').",
                "com.impactgraph.SecurityIntegrationTest#notificationRequiresAuth",
                project.getId());

        seedFinding("SEC-DATA-004", "Hardcoded Encryption Key Detected in Payment Settlement Module",
                "AES-256 secret key literal detected hardcoded in payment processing utility.",
                Severity.MEDIUM, Confidence.CONFIRMED, FindingStatus.RESOLVED,
                "backend/src/main/java/com/impactgraph/payment/PaymentProcessor.java",
                18, 22, "POST /payments/charge", "POST", "Payment Service",
                "PaymentProcessor.java: static final String SECRET_KEY = \"4f8c2b1e...\"",
                "Static encryption keys committed to repository compromise tokenization confidentiality.",
                "Externalize cryptographic keys to vault or environment variable PAYMENT_SECRET_KEY.",
                "com.impactgraph.SecurityIntegrationTest#keyExternalized",
                project.getId());

        // 5. Create Baseline Analysis Run
        AnalysisRun baselineRun = new AnalysisRun();
        baselineRun.setRunType("DEMO_FIXTURE");
        baselineRun.setBaseRevision("main@14ce8c6");
        baselineRun.setTargetRevision("develop@495be18");
        baselineRun.setTimestamp(Instant.now().minusSeconds(1800));
        baselineRun.setTotalFindings(5);
        baselineRun.setCriticalCount(0);
        baselineRun.setHighCount(3);
        baselineRun.setMediumCount(2);
        baselineRun.setLowCount(0);
        baselineRun.setStatus("COMPLETED");
        baselineRun.setProjectId(project.getId());
        baselineRun.setCommitSha("495be187d4dbc84d84da63932e1db262ce227262");
        baselineRun.setSummary("Initial change-impact and security regression baseline for E-Commerce Platform. 8 services, 10 dependencies, 5 security findings mapped to blast radius.");
        runRepository.save(baselineRun);

        log.info("Demo Project '{}' seeded successfully with ID {}.", DEMO_PROJECT_NAME, project.getId());
        return project;
    }

    private Service createService(String name, String description, Project project) {
        Service svc = new Service(name, description, project);
        return serviceRepository.save(svc);
    }

    private void createApi(String name, String method, String path, String description, Service service) {
        Api api = new Api(name, method, path, description, service);
        apiRepository.save(api);
    }

    private void createDep(Service source, Service target, String type, String description) {
        Dependency dep = new Dependency(source, target, type, description);
        dependencyRepository.save(dep);
    }

    private void seedFinding(String ruleId, String title, String description,
                             Severity severity, Confidence confidence, FindingStatus status,
                             String filePath, int startLine, int endLine,
                             String affectedEndpoint, String httpMethod, String affectedComponent,
                             String evidencePath, String explanation, String suggestedRemediation,
                             String suggestedTests, Long projectId) {
        SecurityFinding f = new SecurityFinding();
        f.setRuleId(ruleId);
        f.setTitle(title);
        f.setDescription(description);
        f.setSeverity(severity);
        f.setConfidence(confidence);
        f.setStatus(status);
        f.setFilePath(filePath);
        f.setStartLine(startLine);
        f.setEndLine(endLine);
        f.setAffectedEndpoint(affectedEndpoint);
        f.setHttpMethod(httpMethod);
        f.setAffectedComponent(affectedComponent);
        f.setEvidencePath(evidencePath);
        f.setExplanation(explanation);
        f.setSuggestedRemediation(suggestedRemediation);
        f.setSuggestedTests(suggestedTests);
        f.setProjectId(projectId);
        f.setBaseRevision("develop@c44e1d9");
        f.setTargetRevision("feat/dashboard-redesign@HEAD");
        findingRepository.save(f);
    }
}
