package com.impactgraph.security.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactgraph.entity.AnalysisRun;
import com.impactgraph.repository.AnalysisRunRepository;
import com.impactgraph.repository.SecurityFindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubWebhookTest {

    private GitHubWebhookService webhookService;
    private SecurityFindingRepository findingRepository;
    private AnalysisRunRepository runRepository;
    private ObjectMapper objectMapper;

    private static final String TEST_SECRET = "super-secure-webhook-secret-12345";

    @BeforeEach
    void setUp() {
        findingRepository = mock(SecurityFindingRepository.class);
        runRepository = mock(AnalysisRunRepository.class);
        objectMapper = new ObjectMapper();

        webhookService = new GitHubWebhookService(objectMapper, findingRepository, runRepository);
        ReflectionTestUtils.setField(webhookService, "webhookSecret", TEST_SECRET);

        when(findingRepository.findAll()).thenReturn(List.of());
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void isConfigured_returnsTrueWhenSecretSet() {
        assertTrue(webhookService.isConfigured());
    }

    @Test
    void verifySignature_validHmacSignature_returnsTrue() throws Exception {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hex = HexFormat.of().formatHex(mac.doFinal(payload));
        String signatureHeader = "sha256=" + hex;

        assertTrue(webhookService.verifySignature(payload, signatureHeader));
    }

    @Test
    void verifySignature_invalidSignature_returnsFalse() {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(webhookService.verifySignature(payload, "sha256=invalidhash00000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void processPullRequestEvent_idempotentlyProcessesDeliveryId() {
        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "number": 42,
                        "base": { "sha": "base123" },
                        "head": { "sha": "head456" }
                    },
                    "repository": {
                        "full_name": "AtharvKaner/ImpactGraph"
                    }
                }
                """;

        String deliveryId = "delivery-uuid-abc-123";

        // First delivery -> PROCESSED
        Map<String, Object> res1 = webhookService.processPullRequestEvent(deliveryId, payload);
        assertEquals("PROCESSED", res1.get("status"));
        assertEquals(42, res1.get("pullRequest"));

        // Second delivery with same deliveryId -> IGNORED (idempotent)
        Map<String, Object> res2 = webhookService.processPullRequestEvent(deliveryId, payload);
        assertEquals("IGNORED", res2.get("status"));
        assertEquals("Duplicate delivery ID", res2.get("reason"));
    }

    @Test
    void processPullRequestEvent_durableDeduplication_detectsExistingDeliveryInRepository() {
        String payload = """
                {
                    "action": "synchronize",
                    "pull_request": {
                        "number": 99,
                        "base": { "sha": "base789" },
                        "head": { "sha": "head999" }
                    },
                    "repository": {
                        "full_name": "AtharvKaner/ImpactGraph"
                    }
                }
                """;

        String deliveryId = "delivery-durable-already-in-db";
        when(runRepository.existsByDeliveryId(deliveryId)).thenReturn(true);

        Map<String, Object> result = webhookService.processPullRequestEvent(deliveryId, payload);
        assertEquals("IGNORED", result.get("status"));
        assertEquals("Duplicate delivery ID", result.get("reason"));
        verify(runRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPullRequestEvent_persistsDeliveryIdOnAnalysisRun() {
        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "number": 55,
                        "base": { "sha": "base1" },
                        "head": { "sha": "head2" }
                    },
                    "repository": {
                        "full_name": "AtharvKaner/ImpactGraph"
                    }
                }
                """;

        String deliveryId = "unique-delivery-id-777";
        webhookService.processPullRequestEvent(deliveryId, payload);

        verify(runRepository).saveAndFlush(argThat(run -> deliveryId.equals(run.getDeliveryId())));
    }

    @Test
    void processPullRequestEvent_handlesDatabaseConstraintViolation_resolvesAsIgnored() {
        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "number": 88,
                        "base": { "sha": "baseA" },
                        "head": { "sha": "headB" }
                    },
                    "repository": {
                        "full_name": "AtharvKaner/ImpactGraph"
                    }
                }
                """;

        String deliveryId = "delivery-conflict-unique";
        when(runRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint uk_runs_delivery_id"));

        Map<String, Object> result = webhookService.processPullRequestEvent(deliveryId, payload);
        assertEquals("IGNORED", result.get("status"));
        assertEquals("Duplicate delivery ID", result.get("reason"));
    }

    @Test
    void processPullRequestEvent_simulatesConcurrentDuplicateDeliveries() throws Exception {
        String payload = """
                {
                    "action": "opened",
                    "pull_request": {
                        "number": 100,
                        "base": { "sha": "baseC" },
                        "head": { "sha": "headD" }
                    },
                    "repository": {
                        "full_name": "AtharvKaner/ImpactGraph"
                    }
                }
                """;

        String deliveryId = "concurrent-simulated-uuid-999";
        int threadCount = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Map<String, Object>>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return webhookService.processPullRequestEvent(deliveryId, payload);
            }));
        }

        readyLatch.await();
        startLatch.countDown(); // Release all threads simultaneously

        int processedCount = 0;
        int ignoredCount = 0;
        for (var future : futures) {
            Map<String, Object> res = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            if ("PROCESSED".equals(res.get("status"))) {
                processedCount++;
            } else if ("IGNORED".equals(res.get("status"))) {
                ignoredCount++;
            }
        }
        executor.shutdown();

        // Exactly one concurrent thread must succeed in processing; all others must be safely ignored
        assertEquals(1, processedCount, "Exactly one concurrent thread should process");
        assertEquals(threadCount - 1, ignoredCount, "All other concurrent threads should be ignored");
        verify(runRepository, times(1)).saveAndFlush(any());
    }
}
