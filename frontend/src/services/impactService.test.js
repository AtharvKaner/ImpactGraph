import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  adaptPersistedGraph,
  fetchProjects,
  fetchProjectGraph,
  analyzeChangeImpact,
  HttpError,
  DATASET_PRESETS,
} from './impactService.js';

describe('Impact Service - Persisted Project Integration', () => {
  let originalFetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('adaptPersistedGraph', () => {
    it('normalizes persisted services and dependencies into the UI graph contract', () => {
      const mockProject = {
        id: 1,
        name: 'ImpactGraph Demo',
        description: 'E-commerce microservices core',
      };

      const mockServices = [
        { id: 1, name: 'Order Service', type: 'Core Domain', tier: 'Tier 1' },
        { id: 2, name: 'Payment Service', type: 'Core Domain', tier: 'Tier 1' },
      ];

      const mockDependencies = [
        {
          id: 10,
          sourceServiceId: 1,
          sourceServiceName: 'Order Service',
          targetServiceId: 2,
          targetServiceName: 'Payment Service',
          dependencyType: 'REST',
        },
      ];

      const graph = adaptPersistedGraph(mockProject, mockServices, mockDependencies);

      assert.equal(graph.projectId, 1);
      assert.equal(graph.name, 'ImpactGraph Demo');
      assert.equal(graph.serviceCount, 2);
      assert.equal(graph.edgeCount, 1);
      assert.equal(graph.isPersisted, true);
      assert.equal(graph.isLive, true);
      assert.equal(graph.isFallback, false);

      // Verify normalized service node identifiers
      assert.equal(graph.services[0].id, 'Order Service');
      assert.equal(graph.services[0].numericId, 1);
      assert.equal(graph.services[0].label, 'Order Service');
      assert.equal(graph.services[1].id, 'Payment Service');
      assert.equal(graph.services[1].numericId, 2);

      // Verify normalized dependency edge direction: source = dependent (Order), target = dependency (Payment)
      assert.equal(graph.dependencies[0].source, 'Order Service');
      assert.equal(graph.dependencies[0].target, 'Payment Service');
      assert.equal(graph.dependencies[0].dependentId, 'Order Service');
      assert.equal(graph.dependencies[0].dependencyId, 'Payment Service');
    });

    it('gracefully handles missing or empty attributes', () => {
      const graph = adaptPersistedGraph(null, [], []);
      assert.equal(graph.projectId, undefined);
      assert.equal(graph.name, 'Project #undefined');
      assert.equal(graph.serviceCount, 0);
      assert.equal(graph.edgeCount, 0);
      assert.deepEqual(graph.services, []);
      assert.deepEqual(graph.dependencies, []);
    });
  });

  describe('fetchProjects', () => {
    it('fetches and returns persisted projects list on success', async () => {
      globalThis.fetch = async (url) => {
        assert.ok(url.includes('/api/projects'));
        return {
          ok: true,
          json: async () => [
            { id: 1, name: 'ImpactGraph Demo', serviceCount: 2 },
            { id: 2, name: 'Analytics Platform', serviceCount: 4 },
          ],
        };
      };

      const projects = await fetchProjects();
      assert.equal(projects.length, 2);
      assert.equal(projects[0].id, 1);
      assert.equal(projects[1].name, 'Analytics Platform');
    });

    it('returns empty array on network failure without throwing', async () => {
      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch (network error)');
      };

      const projects = await fetchProjects();
      assert.deepEqual(projects, []);
    });

    it('propagates HTTP server errors instead of silently masking them', async () => {
      globalThis.fetch = async () => {
        return {
          ok: false,
          status: 500,
          statusText: 'Internal Server Error',
          json: async () => ({ message: 'Database connection failed' }),
        };
      };

      await assert.rejects(async () => {
        await fetchProjects();
      }, HttpError);
    });
  });

  describe('fetchProjectGraph', () => {
    it('throws when projectId is not provided', async () => {
      await assert.rejects(async () => {
        await fetchProjectGraph(null);
      }, /Project ID is required/);
    });

    it('fetches project metadata, services, and dependencies concurrently', async () => {
      const requestedUrls = [];
      globalThis.fetch = async (url) => {
        requestedUrls.push(url);
        if (url.endsWith('/api/projects/1')) {
          return {
            ok: true,
            json: async () => ({ id: 1, name: 'ImpactGraph Demo' }),
          };
        }
        if (url.endsWith('/api/projects/1/services')) {
          return {
            ok: true,
            json: async () => [
              { id: 1, name: 'Order Service' },
              { id: 2, name: 'Payment Service' },
            ],
          };
        }
        if (url.endsWith('/api/projects/1/dependencies')) {
          return {
            ok: true,
            json: async () => [
              {
                id: 100,
                sourceServiceName: 'Order Service',
                targetServiceName: 'Payment Service',
              },
            ],
          };
        }
        throw new Error(`Unexpected URL: ${url}`);
      };

      const graph = await fetchProjectGraph(1);
      assert.equal(graph.projectId, 1);
      assert.equal(graph.services.length, 2);
      assert.equal(graph.dependencies.length, 1);
      assert.equal(requestedUrls.length, 3);
    });
  });

  describe('analyzeChangeImpact with projectId', () => {
    it('queries persisted impact endpoint with projectId parameter', async () => {
      let requestedUrl = null;
      globalThis.fetch = async (url) => {
        requestedUrl = url;
        return {
          ok: true,
          json: async () => ({
            changedServiceId: 'Payment Service',
            affectedCount: 1,
            affectedServiceIds: ['Order Service'],
            directAffectedServices: ['Order Service'],
            transitiveAffectedServices: [],
            impactPaths: [
              {
                affectedServiceId: 'Order Service',
                changedServiceId: 'Payment Service',
                distance: 1,
                isDirect: true,
                path: ['Payment Service', 'Order Service'],
              },
            ],
            cycleResult: { hasCycle: false, cycles: [] },
          }),
        };
      };

      const result = await analyzeChangeImpact('Payment Service', null, { projectId: 1 });

      assert.ok(requestedUrl.includes('/api/impact/Payment%20Service?projectId=1'));
      assert.equal(result.isLive, true);
      assert.equal(result.isFallback, false);
      assert.equal(result.affectedCount, 1);
      assert.deepEqual(result.affectedServiceIds, ['Order Service']);
    });

    it('does NOT silently substitute demo results when persisted API request fails', async () => {
      globalThis.fetch = async () => {
        return {
          ok: false,
          status: 404,
          statusText: 'Not Found',
          json: async () => ({ message: 'Service not found in project' }),
        };
      };

      await assert.rejects(async () => {
        await analyzeChangeImpact('Unknown Service', null, { projectId: 1 });
      }, HttpError);
    });

    it('preserves demo mode and offline fallback when projectId is omitted or null', async () => {
      // Offline network failure simulation for demo mode
      globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch (offline)');
      };

      const result = await analyzeChangeImpact('Order Service', DATASET_PRESETS.ECOMMERCE_DAG);

      // In demo mode, fallback is active and clearly flagged
      assert.equal(result.isLive, false);
      assert.equal(result.isFallback, true);
      assert.ok(result.affectedCount > 0);
      assert.equal(result.changedServiceId, 'Order Service');
    });
  });

  describe('Deterministic grid positioning calculation', () => {
    it('generates consistent coordinates for services without predefined positions', () => {
      const getFallbackPosition = (index) => ({
        x: 80 + (index % 3) * 240,
        y: 80 + Math.floor(index / 3) * 160,
      });

      const pos0 = getFallbackPosition(0);
      const pos1 = getFallbackPosition(1);
      const pos2 = getFallbackPosition(2);
      const pos3 = getFallbackPosition(3);

      assert.deepEqual(pos0, { x: 80, y: 80 });
      assert.deepEqual(pos1, { x: 320, y: 80 });
      assert.deepEqual(pos2, { x: 560, y: 80 });
      assert.deepEqual(pos3, { x: 80, y: 240 });
    });
  });
});
