package com.impactgraph.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted security finding identifying an architectural or security regression,
 * rule violation, or exposed attack surface in code revisions.
 */
@Entity
@Table(name = "security_findings", indexes = {
        @Index(name = "idx_findings_rule_id", columnList = "ruleId"),
        @Index(name = "idx_findings_severity", columnList = "severity"),
        @Index(name = "idx_findings_status", columnList = "status"),
        @Index(name = "idx_findings_project", columnList = "projectId")
})
public class SecurityFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String ruleId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Confidence confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FindingStatus status;

    @Column(length = 100)
    private String baseRevision;

    @Column(length = 100)
    private String targetRevision;

    @Column(length = 255)
    private String filePath;

    private Integer startLine;

    private Integer endLine;

    @Column(length = 255)
    private String affectedEndpoint;

    @Column(length = 10)
    private String httpMethod;

    @Column(length = 100)
    private String affectedComponent;

    @Column(columnDefinition = "TEXT")
    private String evidencePath;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String suggestedRemediation;

    @Column(columnDefinition = "TEXT")
    private String suggestedTests;

    @Column(columnDefinition = "TEXT")
    private String verificationNotes;

    private Long projectId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant verifiedAt;

    public SecurityFinding() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public void setConfidence(Confidence confidence) {
        this.confidence = confidence;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public void setBaseRevision(String baseRevision) {
        this.baseRevision = baseRevision;
    }

    public String getTargetRevision() {
        return targetRevision;
    }

    public void setTargetRevision(String targetRevision) {
        this.targetRevision = targetRevision;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public void setEndLine(Integer endLine) {
        this.endLine = endLine;
    }

    public String getAffectedEndpoint() {
        return affectedEndpoint;
    }

    public void setAffectedEndpoint(String affectedEndpoint) {
        this.affectedEndpoint = affectedEndpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getAffectedComponent() {
        return affectedComponent;
    }

    public void setAffectedComponent(String affectedComponent) {
        this.affectedComponent = affectedComponent;
    }

    public String getEvidencePath() {
        return evidencePath;
    }

    public void setEvidencePath(String evidencePath) {
        this.evidencePath = evidencePath;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSuggestedRemediation() {
        return suggestedRemediation;
    }

    public void setSuggestedRemediation(String suggestedRemediation) {
        this.suggestedRemediation = suggestedRemediation;
    }

    public String getSuggestedTests() {
        return suggestedTests;
    }

    public void setSuggestedTests(String suggestedTests) {
        this.suggestedTests = suggestedTests;
    }

    public String getVerificationNotes() {
        return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
        this.verificationNotes = verificationNotes;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecurityFinding that = (SecurityFinding) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
