import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  importRepository,
  compareRevisions,
  fetchLocalWorkspaceInfo,
  fetchWorkspaceRevisions,
  setSession,
  clearSession,
  HttpError,
} from '../services/impactService.js';

class MockSessionStorage {
  constructor() {
    this.items = new Map();
  }
  getItem(key) {
    return this.items.get(key) ?? null;
  }
  setItem(key, value) {
    this.items.set(key, String(value));
  }
  removeItem(key) {
    this.items.delete(key);
  }
  clear() {
    this.items.clear();
  }
}

describe('Real Repository Analysis & Revision Comparison Integration', () => {
  let originalFetch;
  let mockStorage;

  beforeEach(() => {
    mockStorage = new MockSessionStorage();
    globalThis.sessionStorage = mockStorage;
    globalThis.window = { sessionStorage: mockStorage };
    originalFetch = globalThis.fetch;

    // Simulate authenticated user
    setSession({
      token: 'test-jwt-token-12345',
      userId: '1',
      email: 'engineer@impactgraph.local',
      displayName: 'Lead Engineer',
      role: 'DEVELOPER',
    });
  });

  afterEach(() => {
    clearSession();
    globalThis.fetch = originalFetch;
    delete globalThis.sessionStorage;
    delete globalThis.window;
  });

  describe('importRepository()', () => {
    it('sends POST /api/repositories/import with Authorization header and parses response', async () => {
      let capturedUrl = null;
      let capturedOptions = null;

      globalThis.fetch = async (url, options) => {
        capturedUrl = url;
        capturedOptions = options;
        return {
          ok: true,
          status: 201,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({
            projectId: 42,
            projectName: 'AtharvKaner/ImpactGraph',
            sourceType: 'GITHUB',
            repositoryUrl: 'https://github.com/AtharvKaner/ImpactGraph',
            branch: 'feat/dashboard-redesign',
            commitSha: 'a1b2c3d4e5f6',
            serviceCount: 4,
            dependencyCount: 5,
            apiCount: 12,
            securityFindingCount: 2,
            primaryLanguages: ['Java', 'JavaScript'],
            confidence: 'HIGH',
            limitations: 'Static AST analysis',
            services: [
              { id: 1, name: 'RepositoryAnalysisService', tier: 'APPLICATION', type: 'SPRING_SERVICE' },
            ],
            dependencies: [
              { sourceServiceName: 'RepositoryController', targetServiceName: 'RepositoryAnalysisService', dependencyType: 'REST' },
            ],
          }),
        };
      };

      const result = await importRepository({
        sourceType: 'GITHUB',
        repositoryUrl: 'https://github.com/AtharvKaner/ImpactGraph',
        branch: 'feat/dashboard-redesign',
      });

      assert.equal(capturedUrl, '/api/repositories/import');
      assert.equal(capturedOptions.method, 'POST');
      assert.equal(capturedOptions.headers['Authorization'], 'Bearer test-jwt-token-12345');
      assert.equal(result.projectId, 42);
      assert.equal(result.serviceCount, 4);
      assert.equal(result.dependencyCount, 5);
      assert.equal(result.primaryLanguages[0], 'Java');
    });

    it('propagates HttpError when repository import fails validation (e.g. 400 Bad Request)', async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          error: 'Only HTTPS GitHub URLs (https://github.com/...) are allowed',
        }),
      });

      await assert.rejects(
        async () => {
          await importRepository({
            sourceType: 'GITHUB',
            repositoryUrl: 'http://malicious-site.com/repo',
          });
        },
        (err) => {
          assert(err instanceof HttpError || err.isHttpError);
          assert.equal(err.status, 400);
          return true;
        }
      );
    });
  });

  describe('compareRevisions()', () => {
    it('sends POST /api/repositories/compare-revisions and parses blast radius', async () => {
      let capturedUrl = null;
      let capturedOptions = null;

      globalThis.fetch = async (url, options) => {
        capturedUrl = url;
        capturedOptions = options;
        return {
          ok: true,
          status: 200,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({
            projectId: 42,
            baseRevision: 'HEAD~1',
            targetRevision: 'HEAD',
            changedFilesCount: 2,
            changedFiles: [
              {
                filePath: 'backend/src/main/java/com/impactgraph/service/PaymentService.java',
                changeType: 'MODIFIED',
                linesAdded: 15,
                linesDeleted: 3,
                mappedService: 'Payment Service',
              },
            ],
            directlyImpactedServices: ['Payment Service'],
            transitivelyImpactedServices: ['Order Service', 'Storefront Web'],
            totalImpactedCount: 3,
            impactPaths: [
              { affectedServiceId: 'Order Service', changedServiceId: 'Payment Service', distance: 1, path: ['Order Service', 'Payment Service'] },
            ],
            impactedApis: [
              { method: 'POST', path: '/api/payments/refund', controllerName: 'PaymentController', handlerMethod: 'processRefund', riskLevel: 'HIGH' },
            ],
            securityFindingsInScope: [],
            recommendedTests: [
              'com.impactgraph.service.PaymentServiceTest',
              'com.impactgraph.service.OrderServiceTest',
            ],
            confidence: 'STATIC_EVIDENCE',
            limitations: 'Static AST analysis',
          }),
        };
      };

      const result = await compareRevisions({
        projectId: 42,
        baseRevision: 'HEAD~1',
        targetRevision: 'HEAD',
      });

      assert.equal(capturedUrl, '/api/repositories/compare-revisions');
      assert.equal(capturedOptions.method, 'POST');
      assert.equal(result.changedFilesCount, 2);
      assert.equal(result.directlyImpactedServices[0], 'Payment Service');
      assert.equal(result.transitivelyImpactedServices[0], 'Order Service');
      assert.equal(result.impactedApis.length, 1);
      assert.equal(result.recommendedTests.length, 2);
    });

    it('rejects when projectId is missing', async () => {
      await assert.rejects(
        async () => {
          await compareRevisions({
            baseRevision: 'HEAD~1',
            targetRevision: 'HEAD',
          });
        },
        (err) => {
          assert(err instanceof Error);
          assert(err.message.includes('Project ID is required'));
          return true;
        }
      );
    });
  });

  describe('fetchLocalWorkspaceInfo()', () => {
    it('sends GET /api/repositories/local-workspace and returns workspace details', async () => {
      globalThis.fetch = async (url) => {
        assert.equal(url, '/api/repositories/local-workspace');
        return {
          ok: true,
          status: 200,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({
            name: 'ImpactGraph',
            path: 'C:\\Users\\athar\\ImpactGraph',
            branch: 'feat/dashboard-redesign',
            headCommit: '495be187d4db',
            isGitRepository: true,
          }),
        };
      };

      const ws = await fetchLocalWorkspaceInfo();
      assert.equal(ws.name, 'ImpactGraph');
      assert.equal(ws.branch, 'feat/dashboard-redesign');
      assert.equal(ws.isGitRepository, true);
    });
  });

  describe('fetchWorkspaceRevisions()', () => {
    it('sends GET /api/repositories/revisions?limit=10', async () => {
      let capturedUrl = null;
      globalThis.fetch = async (url) => {
        capturedUrl = url;
        return {
          ok: true,
          status: 200,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => [
            { sha: 'a1b2c3d4e5f6', summary: 'feat: add repository analysis' },
            { sha: '495be187d4db', summary: 'feat: security integration' },
          ],
        };
      };

      const revs = await fetchWorkspaceRevisions(10);
      assert.equal(capturedUrl, '/api/repositories/revisions?limit=10');
      assert.equal(revs.length, 2);
      assert.equal(revs[0].sha, 'a1b2c3d4e5f6');
    });
  });
});
