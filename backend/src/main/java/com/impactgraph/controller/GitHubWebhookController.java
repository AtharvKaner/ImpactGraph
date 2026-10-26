package com.impactgraph.controller;

import com.impactgraph.security.analysis.GitHubWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controller receiving GitHub pull request and repository webhooks.
 * Validates HMAC-SHA256 signatures and processes pull requests idempotently.
 */
@RestController
@RequestMapping("/api/github")
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "pull_request") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawPayload
    ) {
        // If webhook secret is configured, verify HMAC signature
        if (webhookService.isConfigured()) {
            byte[] payloadBytes = rawPayload.getBytes(StandardCharsets.UTF_8);
            if (!webhookService.verifySignature(payloadBytes, signature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid HMAC-SHA256 signature"));
            }
        }

        if (!"pull_request".equalsIgnoreCase(eventType)) {
            return ResponseEntity.ok(Map.of("status", "IGNORED", "message", "Event type not monitored: " + eventType));
        }

        Map<String, Object> result = webhookService.processPullRequestEvent(deliveryId, rawPayload);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWebhookStatus() {
        return ResponseEntity.ok(Map.of(
                "configured", webhookService.isConfigured(),
                "monitoredEvents", "pull_request",
                "endpoint", "/api/github/webhook"
        ));
    }
}
