package com.impactgraph.service.repoanalysis;

import com.impactgraph.dto.repoanalysis.*;
import com.impactgraph.entity.*;
import com.impactgraph.repository.*;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.security.analysis.JavaSourceAnalyzer;
import com.impactgraph.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates repository ingestion, architectural analysis, database persistence,
 * and project membership authorization.
 */
@Service
public class RepositoryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisService.class);

    private final RepositoryValidationService validationService;
    private final GitOperationsService gitOperationsService;
    private final RepositoryArchitectureAnalyzer architectureAnalyzer;
    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final ApiRepository apiRepository;
    private final DependencyRepository dependencyRepository;
    private final SecurityFindingRepository findingRepository;
    private final AnalysisRunRepository runRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Autowired
    public RepositoryAnalysisService(RepositoryValidationService validationService,
                                     GitOperationsService gitOperationsService,
                                     RepositoryArchitectureAnalyzer architectureAnalyzer,
                                     ProjectRepository projectRepository,
                                     ServiceRepository serviceRepository,
                                     ApiRepository apiRepository,
                                     DependencyRepository dependencyRepository,
                                     SecurityFindingRepository findingRepository,
                                     AnalysisRunRepository runRepository,
                                     ProjectMembershipRepository membershipRepository,
                                     UserRepository userRepository,
                                     AuditService auditService) {
        this.validationService = validationService;
        this.gitOperationsService = gitOperationsService;
        this.architectureAnalyzer = architectureAnalyzer;
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.apiRepository = apiRepository;
        this.dependencyRepository = dependencyRepository;
        this.findingRepository = findingRepository;
        this.runRepository = runRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Ingests a repository (GitHub or local), parses its architecture, and persists it into a Project.
     */
    @Transactional
    public RepositoryAnalysisResponseDto importAndAnalyze(RepositoryImportRequest request,
                                                         JwtAuthenticatedUser user,
                                                         String ipAddress) {
        if (request == null) {
            throw new IllegalArgumentException("Import request cannot be null");
        }

        String sourceType = (request.getSourceType() != null && !request.getSourceType().isBlank())
                ? request.getSourceType().toUpperCase(Locale.ROOT)
                : "GITHUB";

        Path repoDir = null;
        Path tempDirToDelete = null;
        String repoUrl = null;
        String branch = validationService.validateRevision(request.getBranch());
        String token = validationService.sanitizeToken(request.getPersonalAccessToken());

        try {
            if ("GITHUB".equals(sourceType)) {
                repoUrl = validationService.validateGitHubUrl(request.getRepositoryUrl());
                tempDirToDelete = Files.createTempDirectory("impactgraph-import-");
                repoDir = tempDirToDelete;
                gitOperationsService.cloneRepository(repoUrl, branch, token, repoDir, 45, 100 * 1024 * 1024L);
            } else if ("LOCAL".equals(sourceType)) {
                repoDir = validationService.validateLocalPath(request.getLocalPath());
                repoUrl = repoDir.toString();
            } else {
                throw new IllegalArgumentException("Unsupported source type: " + sourceType + ". Permitted: GITHUB, LOCAL");
            }

            // 1. Resolve commit SHA and active branch
            String commitSha = gitOperationsService.getHeadCommitSha(repoDir);
            String activeBranch = gitOperationsService.getCurrentBranch(repoDir);

            // 2. Perform static architecture analysis
            RepositoryArchitectureAnalyzer.ArchitectureAnalysisResult analysis = architectureAnalyzer.analyze(repoDir);

            // 3. Resolve project name
            String baseProjectName = resolveProjectName(request, repoUrl, repoDir);
            String projectName = generateUniqueProjectName(baseProjectName);

            // 4. Create and persist Project entity
            Project project = new Project();
            project.setName(projectName);
            project.setDescription(request.getDescription() != null && !request.getDescription().isBlank()
                    ? request.getDescription().trim()
                    : String.format("Imported from %s (%s) at commit %s", sourceType, repoUrl, commitSha));
            final Project savedProject = projectRepository.save(project);

            // 5. Grant Project Membership to importing user
            if (user != null && user.getUserId() != null) {
                userRepository.findById(user.getUserId()).ifPresent(appUser -> {
                    ProjectMembership membership = new ProjectMembership();
                    membership.setUser(appUser);
                    membership.setProject(savedProject);
                    membership.setGrantedBy(user.getUserId());
                    membershipRepository.save(membership);
                });
            }

            // 6. Persist Service entities and mapping
            Map<String, com.impactgraph.entity.Service> persistedServices = new HashMap<>();
            for (ServiceSummaryDto svcDto : analysis.services()) {
                com.impactgraph.entity.Service service = new com.impactgraph.entity.Service();
                service.setName(svcDto.name());
                service.setProject(savedProject);
                service.setDescription(String.format("Source: %s:%d | Tier: %s | Type: %s",
                        svcDto.sourcePath(), svcDto.lineNumber(), svcDto.tier(), svcDto.type()));
                service = serviceRepository.save(service);
                persistedServices.put(svcDto.name(), service);
            }

            // 7. Persist APIs
            for (JavaSourceAnalyzer.EndpointDefinition ep : analysis.endpoints()) {
                com.impactgraph.entity.Service ownerSvc = persistedServices.get(ep.controllerName());
                if (ownerSvc != null) {
                    com.impactgraph.entity.Api api = new com.impactgraph.entity.Api();
                    api.setService(ownerSvc);
                    api.setName(ep.handlerMethod());
                    api.setPath(ep.fullPath());
                    api.setMethod(ep.httpMethod());
                    api.setDescription(String.format("%s#%s (%s:%d)", ep.controllerName(), ep.handlerMethod(), ep.sourceFilePath(), ep.lineNumber()));
                    apiRepository.save(api);
                }
            }

            // 8. Persist Dependencies
            int savedEdgeCount = 0;
            for (DependencySummaryDto depDto : analysis.dependencies()) {
                com.impactgraph.entity.Service source = persistedServices.get(depDto.sourceService());
                com.impactgraph.entity.Service target = persistedServices.get(depDto.targetService());

                if (source != null && target != null && !source.getId().equals(target.getId())) {
                    if (!dependencyRepository.existsBySourceServiceIdAndTargetServiceId(source.getId(), target.getId())) {
                        Dependency dep = new Dependency();
                        dep.setSourceService(source);
                        dep.setTargetService(target);
                        dep.setDependencyType(depDto.dependencyType());
                        dep.setDescription(String.format("Evidence: %s:%d [%s]",
                                depDto.evidenceFilePath(), depDto.lineNumber(), depDto.evidenceSnippet()));
                        dependencyRepository.save(dep);
                        savedEdgeCount++;
                    }
                }
            }

            // 9. Persist Security Findings
            for (SecurityFinding finding : analysis.findings()) {
                finding.setProjectId(savedProject.getId());
                finding.setTargetRevision(commitSha);
                findingRepository.save(finding);
            }

            // 10. Record AnalysisRun
            AnalysisRun run = new AnalysisRun();
            run.setRunType("REPOSITORY_IMPORT");
            run.setProjectId(savedProject.getId());
            run.setCommitSha(commitSha);
            run.setTargetRevision(activeBranch);
            run.setTotalFindings(analysis.findings().size());
            run.setStatus("COMPLETED");
            run.setSummary(String.format(
                    "Repository import completed: %d services, %d dependencies, %d APIs, %d security findings from %d source files.",
                    persistedServices.size(), savedEdgeCount, analysis.endpoints().size(), analysis.findings().size(), analysis.scannedFilesCount()
            ));
            run = runRepository.save(run);

            // Audit log
            if (user != null && auditService != null) {
                auditService.logEvent(
                        AuditEventType.PROJECT_CREATED,
                        user.getUserId(), user.getEmail(),
                        toUuid(savedProject.getId()), toUuid(savedProject.getId()),
                        "SUCCESS",
                        "Repository imported as project: " + savedProject.getName(),
                        ipAddress
                );
            }

            return new RepositoryAnalysisResponseDto(
                    savedProject.getId(),
                    savedProject.getName(),
                    savedProject.getDescription(),
                    sourceType,
                    repoUrl,
                    commitSha,
                    activeBranch,
                    persistedServices.size(),
                    savedEdgeCount,
                    analysis.endpoints().size(),
                    analysis.findings().size(),
                    analysis.scannedFilesCount(),
                    analysis.detectedLanguages(),
                    analysis.services(),
                    analysis.dependencies(),
                    run.getId(),
                    "HIGH",
                    "Analysis based on AST, annotations, and explicit import declarations. Dynamic reflection or external message queues (e.g. Kafka/RabbitMQ) require runtime instrumentation."
            );

        } catch (Exception e) {
            log.error("Repository import failed for {}: {}", request.getRepositoryUrl(), e.getMessage(), e);
            throw (e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e.getMessage(), e));
        } finally {
            if (tempDirToDelete != null) {
                gitOperationsService.deleteDirectoryRecursively(tempDirToDelete);
            }
        }
    }

    private String resolveProjectName(RepositoryImportRequest request, String repoUrl, Path repoDir) {
        if (request.getProjectName() != null && !request.getProjectName().isBlank()) {
            return request.getProjectName().trim();
        }
        if (repoUrl != null && repoUrl.startsWith("https://github.com/")) {
            String sub = repoUrl.substring("https://github.com/".length());
            return sub.replace('/', '-');
        }
        if (repoDir != null && repoDir.getFileName() != null) {
            return repoDir.getFileName().toString();
        }
        return "Imported-Project-" + System.currentTimeMillis();
    }

    private String generateUniqueProjectName(String baseName) {
        String name = baseName;
        int counter = 1;
        while (projectRepository.existsByName(name)) {
            name = baseName + " (" + counter + ")";
            counter++;
        }
        return name;
    }

    private UUID toUuid(Long id) {
        if (id == null) return null;
        return new UUID(0L, id);
    }
}
