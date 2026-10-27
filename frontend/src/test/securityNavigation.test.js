import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  fetchSecurityFindings,
  fetchApiInventory,
  verifyFinding,
  fetchAiExplanation,
  fetchAnalysisHistory,
  fetchAuditEvents,
  fetchMyActivity,
  fetchPublicTransparency,
  fetchGitHubStatus,
  isPublicEndpoint,
} from '../services/impactService.js';

describe('Security Analysis & Multi-View Navigation Integration', () => {
  let originalFetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('API Inventory & Security Drift Service', () => {
    it('fetches API inventory from backend when reachable', async () => {
      const mockItems = [
        {
          method: 'DELETE',
          path: '/api/projects/{projectId}',
          controllerName: 'ProjectController',
          handlerMethod: 'deleteProject',
          authRequirement: 'ROLE_BASED',
          requiredRole: 'ROLE_ADMIN',
          hasPreAuthorize: true,
          isPermitAll: false,
          driftStatus: 'UNCHANGED',
          riskLevel: 'HIGH',
        },
      ];

      globalThis.fetch = async (url) => {
        if (url.includes('/api/security/api-inventory')) {
          return {
            ok: true,
            status: 200,
            json: async () => mockItems,
          };
        }
        throw new Error('Unknown URL: ' + url);
      };

      const result = await fetchApiInventory();
      assert.equal(result.length, 1);
      assert.equal(result[0].method, 'DELETE');
      assert.equal(result[0].path, '/api/projects/{projectId}');
      assert.equal(result[0].authRequirement, 'ROLE_BASED');
    });

    it('falls back to local demonstration inventory when network is offline', async () => {
      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch');
      };

      const result = await fetchApiInventory();
      assert.ok(Array.isArray(result));
      assert.ok(result.length > 0);
      assert.ok(result.some((item) => item.path === '/api/auth/login'));
      assert.ok(result.some((item) => item.authRequirement === 'ROLE_BASED'));
    });
  });

  describe('Security Findings & Verification Service', () => {
    it('fetches security findings successfully from backend', async () => {
      const mockFindings = [
        {
          id: 'find-101',
          ruleId: 'SEC-AUTHZ-002',
          title: 'Removed @PreAuthorize Authorization Enforcement',
          severity: 'CRITICAL',
          confidence: 'CONFIRMED',
          status: 'OPEN',
          affectedComponent: 'ProjectController',
          affectedEndpoint: 'DELETE /api/projects/{projectId}',
          suggestedRemediation: 'Re-add @PreAuthorize("hasRole(\'ADMIN\')")',
        },
      ];

      globalThis.fetch = async (url) => {
        if (url.includes('/api/security/findings')) {
          return {
            ok: true,
            status: 200,
            json: async () => mockFindings,
          };
        }
        throw new Error('Unknown URL: ' + url);
      };

      const findings = await fetchSecurityFindings();
      assert.equal(findings.length, 1);
      assert.equal(findings[0].id, 'find-101');
      assert.equal(findings[0].severity, 'CRITICAL');
      assert.equal(findings[0].status, 'OPEN');
    });

    it('verifies finding resolution via backend verification endpoint', async () => {
      let requestedUrl = null;
      let requestedMethod = null;

      globalThis.fetch = async (url, options) => {
        requestedUrl = url;
        requestedMethod = options.method;
        return {
          ok: true,
          status: 200,
          json: async () => ({
            findingId: 'find-101',
            verified: true,
            status: 'VERIFIED',
            message: 'All deterministic AST checks and security regression tests passed.',
            timestamp: new Date().toISOString(),
          }),
        };
      };

      const outcome = await verifyFinding('find-101');
      assert.ok(requestedUrl.includes('/api/security/findings/find-101/verify'));
      assert.equal(requestedMethod, 'POST');
      assert.equal(outcome.verified, true);
      assert.equal(outcome.status, 'VERIFIED');
    });

    it('fetches AI explanation with structured remediation snippet', async () => {
      globalThis.fetch = async (url) => {
        if (url.includes('/api/security/findings/find-101/ai-explanation')) {
          return {
            ok: true,
            status: 200,
            json: async () => ({
              findingId: 'find-101',
              ruleId: 'SEC-AUTHZ-002',
              threatSummary: 'Missing authorization check allows unprivileged users to delete projects.',
              impactRationale: 'Direct controller invocation executes delete cascade.',
              suggestedRemediation: 'Add @PreAuthorize check.',
              remediationCodeSnippet: '@PreAuthorize("hasRole(\'ADMIN\')")',
              suggestedRegressionTests: ['ProjectSecurityIntegrationTest#deleteProject_nonAdmin_forbidden'],
              limitationsAndUncertainty: 'Deterministic finding.',
              isAiGenerated: false,
              modelProvider: 'Security Knowledge Engine',
            }),
          };
        }
        throw new Error('Unknown URL');
      };

      const explanation = await fetchAiExplanation('find-101');
      assert.equal(explanation.findingId, 'find-101');
      assert.ok(explanation.remediationCodeSnippet.includes('@PreAuthorize'));
      assert.ok(explanation.suggestedRegressionTests.length > 0);
    });
  });

  describe('Audit & Public Transparency Endpoints', () => {
    it('identifies /api/transparency/public and /api/github/webhook as public endpoints', () => {
      assert.equal(isPublicEndpoint('/api/transparency/public'), true);
      assert.equal(isPublicEndpoint('/api/github/webhook'), true);
      assert.equal(isPublicEndpoint('/api/github/status'), true);
      assert.equal(isPublicEndpoint('/api/health'), true);
      assert.equal(isPublicEndpoint('/api/security/findings'), false);
      assert.equal(isPublicEndpoint('/api/audit/events'), false);
    });

    it('fetches public transparency statements without throwing', async () => {
      const data = await fetchPublicTransparency();
      assert.ok(data);
      assert.ok(Array.isArray(data.dataCollected));
      assert.equal(data.dataStorage.database, 'PostgreSQL');
      assert.ok(data.accessControl.ADMIN);
      assert.ok(data.accessControl.DEVELOPER);
    });

    it('fetches GitHub webhook status', async () => {
      const status = await fetchGitHubStatus();
      assert.ok(status);
      assert.equal(status.endpoint, '/api/github/webhook');
      assert.equal(status.monitoredEvents, 'pull_request');
    });

    it('fetches analysis run history with fallback when offline', async () => {
      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch');
      };
      const history = await fetchAnalysisHistory();
      assert.ok(Array.isArray(history));
      assert.ok(history.length > 0);
      assert.equal(history[0].status, 'COMPLETED');
    });

    it('fetches audit events and user activity with fallback when offline', async () => {
      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch');
      };
      const audit = await fetchAuditEvents();
      assert.ok(Array.isArray(audit.content));
      assert.ok(audit.content.length > 0);

      const activity = await fetchMyActivity();
      assert.ok(activity.email);
      assert.ok(Array.isArray(activity.recentActivity));
    });
  });
});

