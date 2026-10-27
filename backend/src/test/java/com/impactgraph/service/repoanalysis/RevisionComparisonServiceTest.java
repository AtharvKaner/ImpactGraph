package com.impactgraph.service.repoanalysis;

import com.impactgraph.dto.repoanalysis.ChangedFileDto;
import com.impactgraph.dto.repoanalysis.RevisionComparisonRequest;
import com.impactgraph.dto.repoanalysis.RevisionComparisonResponseDto;
import com.impactgraph.entity.AnalysisRun;
import com.impactgraph.entity.Project;
import com.impactgraph.entity.Service;
import com.impactgraph.graph.DefaultImpactAnalyzer;
import com.impactgraph.graph.DependencyGraph;
import com.impactgraph.repository.AnalysisRunRepository;
import com.impactgraph.repository.ProjectRepository;
import com.impactgraph.repository.SecurityFindingRepository;
import com.impactgraph.repository.ServiceRepository;
import com.impactgraph.security.JwtAuthenticatedUser;
import com.impactgraph.service.DependencyService;
import com.impactgraph.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class RevisionComparisonServiceTest {

    private RevisionComparisonService comparisonService;
    private ProjectService projectService;
    private ProjectRepository projectRepository;
    private ServiceRepository serviceRepository;
    private SecurityFindingRepository findingRepository;
    private AnalysisRunRepository runRepository;
    private DependencyService dependencyService;
    private GitOperationsService gitOperationsService;
    private RepositoryValidationService validationService;

    @BeforeEach
    void setUp() {
        projectService = Mockito.mock(ProjectService.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        serviceRepository = Mockito.mock(ServiceRepository.class);
        findingRepository = Mockito.mock(SecurityFindingRepository.class);
        runRepository = Mockito.mock(AnalysisRunRepository.class);
        dependencyService = Mockito.mock(DependencyService.class);
        gitOperationsService = Mockito.mock(GitOperationsService.class);
        validationService = Mockito.mock(RepositoryValidationService.class);

        when(validationService.validateRevision(anyString())).thenAnswer(i -> i.getArgument(0));
        when(validationService.validateRevision(null)).thenReturn("HEAD");

        comparisonService = new RevisionComparisonService(
                projectService,
                projectRepository,
                serviceRepository,
                findingRepository,
                runRepository,
                dependencyService,
                new DefaultImpactAnalyzer(),
                gitOperationsService,
                validationService
        );
    }

    @Test
    @DisplayName("Revision comparison maps changed files to services and calculates downstream blast radius")
    void compareRevisions_changedService_computesBlastRadius() {
        Long projectId = 100L;
        Project project = new Project("Test Platform", "Description");
        project.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Services: Checkout -> Payment (Checkout depends on Payment)
        Service checkout = new Service("CheckoutService", "Source: CheckoutService.java", project);
        checkout.setId(1L);
        Service payment = new Service("PaymentService", "Source: PaymentService.java", project);
        payment.setId(2L);
        when(serviceRepository.findByProjectId(projectId)).thenReturn(List.of(checkout, payment));

        // DependencyGraph: CheckoutService -> PaymentService
        DependencyGraph graph = DependencyGraph.builder()
                .addService("CheckoutService")
                .addService("PaymentService")
                .addDependency("CheckoutService", "PaymentService")
                .build();
        when(dependencyService.buildDependencyGraphForProject(projectId)).thenReturn(graph);

        // Git diff indicates PaymentService.java was modified
        when(gitOperationsService.getDiffBetweenRevisions(any(Path.class), anyString(), anyString()))
                .thenReturn(List.of(new ChangedFileDto("src/main/java/PaymentService.java", "MODIFIED", null, null)));

        when(runRepository.save(any(AnalysisRun.class))).thenAnswer(i -> {
            AnalysisRun r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RevisionComparisonRequest request = new RevisionComparisonRequest(projectId, "HEAD~1", "HEAD");
        JwtAuthenticatedUser user = new JwtAuthenticatedUser(UUID.randomUUID(), "dev@test.io", "ROLE_DEVELOPER");

        RevisionComparisonResponseDto response = comparisonService.compareRevisions(request, user, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.directlyChangedServices()).containsExactly("PaymentService");
        assertThat(response.directlyAffectedServices()).containsExactly("CheckoutService");
        assertThat(response.totalAffectedCount()).isEqualTo(1);
        assertThat(response.impactPaths()).hasSize(1);
        assertThat(response.impactPaths().get(0).path()).containsExactly("CheckoutService", "PaymentService");

        // Verify evidence-based explanations
        assertThat(response.explanations()).isNotEmpty();
        assertThat(response.explanations())
                .anyMatch(e -> e.targetId().equals("PaymentService") && !e.isPotentialImpactOnly());
        assertThat(response.explanations())
                .anyMatch(e -> e.targetId().equals("CheckoutService") && e.isPotentialImpactOnly() && "DIRECT".equals(e.impactType()));
    }

    @Test
    @DisplayName("Revision comparison with line hunks captures line numbers and ranges in changed files")
    void compareRevisions_withLineMetrics_populatesHunksAndEvidence() {
        Long projectId = 150L;
        Project project = new Project("E-Commerce Test", "Description");
        project.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        Service payment = new Service("PaymentService", "Source: src/PaymentService.java:10", project);
        payment.setId(5L);
        when(serviceRepository.findByProjectId(projectId)).thenReturn(List.of(payment));

        DependencyGraph graph = DependencyGraph.builder().addService("PaymentService").build();
        when(dependencyService.buildDependencyGraphForProject(projectId)).thenReturn(graph);

        when(gitOperationsService.getDiffBetweenRevisions(any(Path.class), anyString(), anyString()))
                .thenReturn(List.of(new ChangedFileDto(
                        "src/PaymentService.java", "MODIFIED", null, null,
                        12, 4, List.of("L45-L52"), "+ double refundAmount = order.getTotal();"
                )));

        when(runRepository.save(any(AnalysisRun.class))).thenAnswer(i -> {
            AnalysisRun r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RevisionComparisonRequest request = new RevisionComparisonRequest(projectId, "HEAD~1", "HEAD");
        JwtAuthenticatedUser user = new JwtAuthenticatedUser(UUID.randomUUID(), "dev@test.io", "ROLE_DEVELOPER");

        RevisionComparisonResponseDto response = comparisonService.compareRevisions(request, user, "127.0.0.1");

        assertThat(response.changedFiles()).hasSize(1);
        ChangedFileDto file = response.changedFiles().get(0);
        assertThat(file.linesAdded()).isEqualTo(12);
        assertThat(file.linesDeleted()).isEqualTo(4);
        assertThat(file.changedLineRanges()).containsExactly("L45-L52");
        assertThat(file.mappedServiceName()).isEqualTo("PaymentService");

        assertThat(response.explanations()).isNotEmpty();
        assertThat(response.explanations().get(0).sourceEvidence()).contains("L45-L52");
    }

    @Test
    @DisplayName("Unrelated file changes produce zero affected services")
    void compareRevisions_unrelatedFile_zeroBlastRadius() {
        Long projectId = 200L;
        Project project = new Project("Documentation Repo", "Description");
        project.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        Service svc = new Service("CoreService", "Source: CoreService.java", project);
        svc.setId(10L);
        when(serviceRepository.findByProjectId(projectId)).thenReturn(List.of(svc));

        DependencyGraph graph = DependencyGraph.builder().addService("CoreService").build();
        when(dependencyService.buildDependencyGraphForProject(projectId)).thenReturn(graph);

        when(gitOperationsService.getDiffBetweenRevisions(any(Path.class), anyString(), anyString()))
                .thenReturn(List.of(new ChangedFileDto("README.md", "MODIFIED", null, null)));

        when(runRepository.save(any(AnalysisRun.class))).thenAnswer(i -> {
            AnalysisRun r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RevisionComparisonRequest request = new RevisionComparisonRequest(projectId, "HEAD~1", "HEAD");
        JwtAuthenticatedUser user = new JwtAuthenticatedUser(UUID.randomUUID(), "dev@test.io", "ROLE_DEVELOPER");

        RevisionComparisonResponseDto response = comparisonService.compareRevisions(request, user, "127.0.0.1");

        assertThat(response.directlyChangedServices()).isEmpty();
        assertThat(response.totalAffectedCount()).isEqualTo(0);
        assertThat(response.impactPaths()).isEmpty();
    }
}

