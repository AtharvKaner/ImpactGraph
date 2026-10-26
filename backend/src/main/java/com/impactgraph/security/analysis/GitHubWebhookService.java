package com.impactgraph.security.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactgraph.entity.AnalysisRun;
import com.impactgraph.entity.SecurityFinding;
import com.impactgraph.repository.AnalysisRunRepository;
import com.impactgraph.repository.SecurityFindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service processing GitHub Pull Request webhooks with HMAC-SHA256 signature
 * verification, idempotent delivery tracking, and automated security check summaries.
 */
@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    @Value("${github.webhook.secret:${GITHUB_WEBHOOK_SECRET:}}")
    private String webhookSecret;

    private final ObjectMapper objectMapper;
    private final SecurityFindingRepository findingRepository;
    private final AnalysisRunRepository runRepository;
    private final Set<String> processedDeliveryIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Autowired
    public GitHubWebhookService(ObjectMapper objectMapper,
                                SecurityFindingRepository findingRepository,
                                AnalysisRunRepository runRepository) {
        this.objectMapper = objectMapper;
        this.findingRepository = findingRepository;
        this.runRepository = runRepository;
    }

    public boolean isConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /**
     * Verifies HMAC-SHA256 signature against request body bytes.
     */
    public boolean verifySignature(byte[] payloadBytes, String signatureHeader) {
        if (!isConfigured()) {
            // When not configured, reject external webhook calls for safety unless in test mock
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            String expectedHash = signatureHeader.substring("sha256=".length());
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] calculatedHash = mac.doFinal(payloadBytes);
            String calculatedHex = HexFormat.of().formatHex(calculatedHash);

            return MessageDigest.isEqual(
                    calculatedHex.getBytes(StandardCharsets.UTF_8),
                    expectedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify GitHub webhook HMAC signature", e);
            return false;
        }
    }

    /**
     * Handles pull_request webhook payload idempotently.
     */
    public Map<String, Object> processPullRequestEvent(String deliveryId, String payloadJson) {
        if (deliveryId != null) {
            if (!processedDeliveryIds.add(deliveryId)) {
                log.info("Duplicate GitHub delivery ID {} ignored (in-memory concurrent deduplication).", deliveryId);
                return Map.of("status", "IGNORED", "reason", "Duplicate delivery ID");
            }
            if (runRepository.existsByDeliveryId(deliveryId)) {
                log.info("Duplicate GitHub delivery ID {} ignored (durable database deduplication).", deliveryId);
                return Map.of("status", "IGNORED", "reason", "Duplicate delivery ID");
            }
        }

        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String action = root.path("action").asText();
            JsonNode pr = root.path("pull_request");

            if (pr.isMissingNode()) {
                if (deliveryId != null) processedDeliveryIds.remove(deliveryId);
                return Map.of("status", "SKIPPED", "reason", "Not a pull_request event");
            }

            int prNumber = pr.path("number").asInt();
            String baseSha = pr.path("base").path("sha").asText();
            String headSha = pr.path("head").path("sha").asText();
            String repoFullName = root.path("repository").path("full_name").asText();

            log.info("Processing PR #{} in {} (action: {}, base: {}, head: {})",
                    prNumber, repoFullName, action, baseSha, headSha);

            // Create analysis run record
            AnalysisRun run = new AnalysisRun();
            run.setRunType("PULL_REQUEST");
            run.setBaseRevision(baseSha);
            run.setTargetRevision(headSha);
            run.setPullRequestNumber(String.valueOf(prNumber));
            run.setCommitSha(headSha);
            run.setDeliveryId(deliveryId);
            run.setTimestamp(Instant.now());
            run.setStatus("COMPLETED");

            // Evaluate current findings for this PR
            List<SecurityFinding> findings = findingRepository.findAll();
            int critical = 0;
            int high = 0;
            int medium = 0;
            int low = 0;

            for (SecurityFinding f : findings) {
                switch (f.getSeverity()) {
                    case CRITICAL -> critical++;
                    case HIGH -> high++;
                    case MEDIUM -> medium++;
                    case LOW -> low++;
                    default -> {}
                }
            }

            run.setTotalFindings(findings.size());
            run.setCriticalCount(critical);
            run.setHighCount(high);
            run.setMediumCount(medium);
            run.setLowCount(low);

            String conclusion = (critical > 0 || high > 0) ? "ACTION_REQUIRED" : "NEUTRAL";
            String summary = String.format(
                    "ImpactGraph PR Analysis: %d total findings (%d Critical, %d High, %d Medium). Check status: %s",
                    findings.size(), critical, high, medium, conclusion
            );
            run.setSummary(summary);
            run = runRepository.saveAndFlush(run);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "PROCESSED");
            result.put("runId", run.getId());
            result.put("pullRequest", prNumber);
            result.put("conclusion", conclusion);
            result.put("findingsTotal", findings.size());
            result.put("critical", critical);
            result.put("high", high);
            result.put("summary", summary);
            return result;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Duplicate delivery ID {} insertion conflict caught by database unique constraint (concurrent idempotent).", deliveryId);
            return Map.of("status", "IGNORED", "reason", "Duplicate delivery ID");
        } catch (Exception e) {
            if (deliveryId != null) {
                processedDeliveryIds.remove(deliveryId);
            }
            log.error("Failed to process GitHub pull request event", e);
            return Map.of("status", "ERROR", "message", "Internal processing error");
        }
    }
}
