package com.impactgraph.service.repoanalysis;

import com.impactgraph.dto.ApiInventoryItemDto;
import com.impactgraph.dto.ImpactPathDto;
import com.impactgraph.dto.SecurityFindingDto;
import com.impactgraph.dto.repoanalysis.ChangedFileDto;
import com.impactgraph.dto.repoanalysis.RevisionComparisonRequest;
import com.impactgraph.dto.repoanalysis.RevisionComparisonResponseDto;
import com.impactgraph.entity.AnalysisRun;
import com.impactgraph.entity.Project;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.graph.DependencyGraph;
import com.impactgraph.graph.ImpactAnalysisResult;
import com.impactgraph.graph.ImpactAnalyzer;
import com.impactgraph.graph.ImpactPath;
import com.impactgraph.repository.AnalysisRunRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.SecurityFindingRepository;
import com.impactgraph.repository.ServiceRepository;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.DependencyService;
import com.impactgraph.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Compares two Git revisions, maps modified source files to architectural components,
 * traverses dependency graphs to calculate blast radius, and recommends targeted regression tests.
 */
@Service
public class RevisionComparisonService {

    private static final Logger log = LoggerFactory.getLogger(RevisionComparisonService.class);

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final SecurityFindingRepository findingRepository;
    private final AnalysisRunRepository runRepository;
    private final DependencyService dependencyService;
    private final ImpactAnalyzer impactAnalyzer;
    private final GitOperationsService gitOperationsService;
    private final RepositoryValidationService validationService;

    @Autowired
    public RevisionComparisonService(ProjectService projectService,
                                     ProjectRepository projectRepository,
                                     ServiceRepository serviceRepository,
                                     SecurityFindingRepository findingRepository,
                                     AnalysisRunRepository runRepository,
                                     DependencyService dependencyService,
                                     ImpactAnalyzer impactAnalyzer,
                                     GitOperationsService gitOperationsService,
                                     RepositoryValidationService validationService) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.findingRepository = findingRepository;
        this.runRepository = runRepository;
        this.dependencyService = dependencyService;
        this.impactAnalyzer = impactAnalyzer;
        this.gitOperationsService = gitOperationsService;
        this.validationService = validationService;
    }

    /**
     * Executes revision comparison and change-impact blast radius evaluation.
     */
    @Transactional
    public RevisionComparisonResponseDto compareRevisions(RevisionComparisonRequest request,
                                                         JwtAuthenticatedUser user,
                                                         String ipAddress) {
        if (request == null || request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required for revision comparison");
        }

        Long projectId = request.getProjectId();
        projectService.enforceProjectAccess(user, projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with ID: " + projectId));

        String baseRevision = validationService.validateRevision(request.getBaseRevision());
        String targetRevision = validationService.validateRevision(
                request.getTargetRevision() != null ? request.getTargetRevision() : "HEAD"
        );

        Path repoDir = resolveRepositoryDirectory(request, project);
        Path tempDirToDelete = null;

        try {
            if (repoDir == null && request.getRepositoryUrl() != null) {
                String safeUrl = validationService.validateGitHubUrl(request.getRepositoryUrl());
                String token = validationService.sanitizeToken(request.getPersonalAccessToken());
                tempDirToDelete = Files.createTempDirectory("impactgraph-diff-");
                repoDir = tempDirToDelete;
                gitOperationsService.cloneRepository(safeUrl, targetRevision, token, repoDir, 45, 100 * 1024 * 1024L);
            }

            if (repoDir == null) {
                // If analyzing current workspace
                repoDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            }

            // 1. Get changed files from git diff
            List<ChangedFileDto> changedFiles = gitOperationsService.getDiffBetweenRevisions(
                    repoDir, baseRevision, targetRevision
            );

            // 2. Fetch project's services and dependencies
            List<com.impactgraph.entity.Service> services = serviceRepository.findByProjectId(projectId);
            DependencyGraph graph = dependencyService.buildDependencyGraphForProject(projectId);

            // 3. Map changed files to service nodes
            Set<String> directlyChangedServices = new TreeSet<>();
            List<ChangedFileDto> mappedFiles = new ArrayList<>();

            for (ChangedFileDto file : changedFiles) {
                String mappedSvc = mapFileToService(file.filePath(), services);
                if (mappedSvc != null) {
                    directlyChangedServices.add(mappedSvc);
                    mappedFiles.add(new ChangedFileDto(file.filePath(), file.changeType(), file.oldPath(), mappedSvc));
                } else {
                    mappedFiles.add(file);
                }
            }

            // 4. Calculate blast radius across the graph for each changed service
            Set<String> allAffected = new TreeSet<>();
            Set<String> directAffected = new TreeSet<>();
            Set<String> transitiveAffected = new TreeSet<>();
            Map<String, ImpactPathDto> pathMap = new LinkedHashMap<>();

            for (String changedSvc : directlyChangedServices) {
                if (graph.hasService(changedSvc)) {
                    ImpactAnalysisResult result = impactAnalyzer.analyzeImpact(graph, changedSvc);
                    allAffected.addAll(result.affectedServiceIds());
                    directAffected.addAll(result.getDirectAffectedServices());
                    transitiveAffected.addAll(result.getTransitiveAffectedServices());

                    for (ImpactPath p : result.getSortedImpactPaths()) {
                        pathMap.putIfAbsent(p.affectedServiceId(), ImpactPathDto.from(p));
                    }
                }
            }

            // 5. Correlate impacted APIs
            List<ApiInventoryItemDto> impactedApis = new ArrayList<>();
            for (com.impactgraph.entity.Service svc : services) {
                if (allAffected.contains(svc.getName()) || directlyChangedServices.contains(svc.getName())) {
                    if (svc.getApis() != null) {
                        for (com.impactgraph.entity.Api api : svc.getApis()) {
                            impactedApis.add(new ApiInventoryItemDto(
                                    api.getMethod(),
                                    api.getPath(),
                                    svc.getName(),
                                    api.getName() != null ? api.getName() : "handleRequest",
                                    "AUTHENTICATED",
                                    null,
                                    false,
                                    false,
                                    "Spring Security",
                                    List.of(),
                                    "UNCHANGED",
                                    "MEDIUM"
                            ));
                        }
                    }
                }
            }

            // 6. Security findings in blast radius
            List<SecurityFinding> allFindings = findingRepository.findAll();
            List<SecurityFindingDto> relevantFindings = new ArrayList<>();
            for (SecurityFinding f : allFindings) {
                if (Objects.equals(f.getProjectId(), projectId) || projectService.isDemoProject(projectId)) {
                    if (allAffected.contains(f.getAffectedComponent()) || directlyChangedServices.contains(f.getAffectedComponent())) {
                        relevantFindings.add(SecurityFindingDto.from(f));
                    }
                }
            }

            // 7. Recommended targeted regression tests
            List<String> recommendedTests = recommendTargetedTests(repoDir, directlyChangedServices, allAffected);

            // 8. Record AnalysisRun in database
            AnalysisRun run = new AnalysisRun();
            run.setRunType("REVISION_COMPARISON");
            run.setProjectId(projectId);
            run.setBaseRevision(baseRevision);
            run.setTargetRevision(targetRevision);
            run.setTotalFindings(relevantFindings.size());
            run.setTimestamp(Instant.now());
            run.setStatus("COMPLETED");
            run.setSummary(String.format(
                    "Revision comparison %s -> %s: %d changed files, %d directly modified services, %d affected downstream components.",
                    baseRevision, targetRevision, changedFiles.size(), directlyChangedServices.size(), allAffected.size()
            ));
            run = runRepository.save(run);

            return new RevisionComparisonResponseDto(
                    projectId,
                    baseRevision,
                    targetRevision,
                    mappedFiles,
                    new ArrayList<>(directlyChangedServices),
                    new ArrayList<>(directAffected),
                    new ArrayList<>(transitiveAffected),
                    allAffected.size(),
                    new ArrayList<>(pathMap.values()),
                    impactedApis,
                    relevantFindings,
                    recommendedTests,
                    "HIGH",
                    "Blast radius calculated via static AST and package dependency analysis. Runtime RPCs or reflection may induce uncaptured edges.",
                    run.getId()
            );

        } catch (Exception e) {
            log.error("Revision comparison failed for project {}: {}", projectId, e.getMessage(), e);
            throw (e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e.getMessage(), e));
        } finally {
            if (tempDirToDelete != null) {
                gitOperationsService.deleteDirectoryRecursively(tempDirToDelete);
            }
        }
    }

    private Path resolveRepositoryDirectory(RevisionComparisonRequest request, Project project) {
        if (request.getLocalPath() != null && !request.getLocalPath().isBlank()) {
            return validationService.validateLocalPath(request.getLocalPath());
        }

        String desc = project.getDescription();
        if (desc != null && desc.contains("Imported from LOCAL (")) {
            try {
                String sub = desc.substring(desc.indexOf("Imported from LOCAL (") + 21);
                String rawPath = sub.substring(0, sub.indexOf(")"));
                Path p = Paths.get(rawPath);
                if (Files.exists(p) && Files.isDirectory(p)) {
                    return p;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private String mapFileToService(String filePath, List<com.impactgraph.entity.Service> services) {
        if (filePath == null) return null;
        String normalized = filePath.replace('\\', '/').toLowerCase(Locale.ROOT);

        for (com.impactgraph.entity.Service svc : services) {
            String svcName = svc.getName().toLowerCase(Locale.ROOT);
            if (normalized.contains("/" + svcName + ".") || normalized.contains("/" + svcName + "/") || normalized.endsWith(svcName + ".java")) {
                return svc.getName();
            }
            if (svc.getDescription() != null && svc.getDescription().toLowerCase(Locale.ROOT).contains(normalized)) {
                return svc.getName();
            }
        }

        // Fuzzy match on class name
        String fileName = Paths.get(filePath).getFileName().toString().replaceAll("\\.[^.]+$", "").toLowerCase(Locale.ROOT);
        for (com.impactgraph.entity.Service svc : services) {
            if (svc.getName().toLowerCase(Locale.ROOT).equals(fileName)) {
                return svc.getName();
            }
        }

        return null;
    }

    private List<String> recommendTargetedTests(Path repoDir, Set<String> changed, Set<String> affected) {
        Set<String> tests = new LinkedHashSet<>();
        Set<String> searchTargets = new HashSet<>(changed);
        searchTargets.addAll(affected);

        for (String target : searchTargets) {
            tests.add(target + "Test");
            tests.add(target + "IntegrationTest");
        }

        // Check if matching test files actually exist on disk
        if (repoDir != null && Files.exists(repoDir)) {
            List<String> verifiedTests = new ArrayList<>();
            for (String testName : tests) {
                try (var stream = Files.walk(repoDir, 6)) {
                    boolean exists = stream.anyMatch(p -> p.getFileName().toString().startsWith(testName));
                    if (exists) {
                        verifiedTests.add(testName);
                    }
                } catch (Exception ignored) {}
            }
            if (!verifiedTests.isEmpty()) {
                return verifiedTests;
            }
        }

        return new ArrayList<>(tests).subList(0, Math.min(tests.size(), 5));
    }
}
