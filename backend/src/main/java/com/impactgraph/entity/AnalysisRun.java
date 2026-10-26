package com.impactgraph.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted record of a security & change-impact analysis run executed
 * via manual dashboard trigger, Git revision comparison, or GitHub PR webhook.
 */
@Entity
@Table(name = "analysis_runs", indexes = {
        @Index(name = "idx_runs_timestamp", columnList = "timestamp"),
        @Index(name = "idx_runs_project", columnList = "projectId"),
        @Index(name = "idx_runs_sha", columnList = "commitSha")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_runs_delivery_id", columnNames = {"deliveryId"})
})
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String runType; // MANUAL, GIT_REVISION, PULL_REQUEST, DEMO_FIXTURE

    @Column(length = 100)
    private String baseRevision;

    @Column(length = 100)
    private String targetRevision;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    private int totalFindings;

    private int criticalCount;

    private int highCount;

    private int mediumCount;

    private int lowCount;

    @Column(nullable = false, length = 30)
    private String status; // COMPLETED, FAILED, WARNING

    private Long projectId;

    @Column(length = 50)
    private String pullRequestNumber;

    @Column(length = 100)
    private String commitSha;

    @Column(length = 100, unique = true)
    private String deliveryId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    public AnalysisRun() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getTotalFindings() {
        return totalFindings;
    }

    public void setTotalFindings(int totalFindings) {
        this.totalFindings = totalFindings;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }

    public int getHighCount() {
        return highCount;
    }

    public void setHighCount(int highCount) {
        this.highCount = highCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(int mediumCount) {
        this.mediumCount = mediumCount;
    }

    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        this.lowCount = lowCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getPullRequestNumber() {
        return pullRequestNumber;
    }

    public void setPullRequestNumber(String pullRequestNumber) {
        this.pullRequestNumber = pullRequestNumber;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalysisRun that = (AnalysisRun) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
