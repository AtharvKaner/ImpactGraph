import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  fetchProjectGraph,
  adaptPersistedGraph,
  adaptGraphResponse,
  analyzeChangeImpact,
  resolveServiceMetadata,
  DEMO_SERVICE_METADATA,
  setSession,
  clearSession,
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

describe('Demo Project Graph Rendering & Regression Tests', () => {
  let originalFetch;
  let mockStorage;

  const mockDemoProject = {
    id: 1,
    name: '[Demo] E-Commerce Platform',
    description: 'Realistic 8-service e-commerce cloud platform with client, application, domain, and infrastructure tiers.',
    serviceCount: 8,
  };

  const mockDemoServices = [
    { id: 1, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Storefront Web', description: 'User-facing web application portal and edge gateway (Client Tier)', apiCount: 3 },
    { id: 2, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Checkout Service', description: 'Checkout orchestration and multi-step cart engine (Application Tier)', apiCount: 2 },
    { id: 3, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Auth Service', description: 'Identity provider, JWT token issuance, and credential verification (Infrastructure Tier)', apiCount: 3 },
    { id: 4, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Order Service', description: 'Order lifecycle management, status persistence, and cart validation (Domain Tier)', apiCount: 3 },
    { id: 5, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Payment Service', description: 'Payment gateway settlement, refund processing, and audit hooks (Domain Tier)', apiCount: 2 },
    { id: 6, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Inventory Service', description: 'Real-time SKU stock levels and warehouse reservation (Domain Tier)', apiCount: 3 },
    { id: 7, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Notification Service', description: 'Customer dispatch service for order receipts and security alerts (Integration Tier)', apiCount: 2 },
    { id: 8, projectId: 1, projectName: '[Demo] E-Commerce Platform', name: 'Audit Logger', description: 'Immutable compliance ledger and CNS security audit trail (Security Tier)', apiCount: 2 },
  ];

  const mockDemoDependencies = [
    { id: 1, projectId: 1, sourceServiceId: 1, sourceServiceName: 'Storefront Web', targetServiceId: 2, targetServiceName: 'Checkout Service', dependencyType: 'REST' },
    { id: 2, projectId: 1, sourceServiceId: 1, sourceServiceName: 'Storefront Web', targetServiceId: 3, targetServiceName: 'Auth Service', dependencyType: 'REST' },
    { id: 3, projectId: 1, sourceServiceId: 2, sourceServiceName: 'Checkout Service', targetServiceId: 4, targetServiceName: 'Order Service', dependencyType: 'REST' },
    { id: 4, projectId: 1, sourceServiceId: 2, sourceServiceName: 'Checkout Service', targetServiceId: 5, targetServiceName: 'Payment Service', dependencyType: 'REST' },
    { id: 5, projectId: 1, sourceServiceId: 4, sourceServiceName: 'Order Service', targetServiceId: 5, targetServiceName: 'Payment Service', dependencyType: 'REST' },
    { id: 6, projectId: 1, sourceServiceId: 4, sourceServiceName: 'Order Service', targetServiceId: 6, targetServiceName: 'Inventory Service', dependencyType: 'REST' },
    { id: 7, projectId: 1, sourceServiceId: 4, sourceServiceName: 'Order Service', targetServiceId: 8, targetServiceName: 'Audit Logger', dependencyType: 'EVENT' },
    { id: 8, projectId: 1, sourceServiceId: 5, sourceServiceName: 'Payment Service', targetServiceId: 7, targetServiceName: 'Notification Service', dependencyType: 'EVENT' },
    { id: 9, projectId: 1, sourceServiceId: 5, sourceServiceName: 'Payment Service', targetServiceId: 8, targetServiceName: 'Audit Logger', dependencyType: 'EVENT' },
    { id: 10, projectId: 1, sourceServiceId: 3, sourceServiceName: 'Auth Service', targetServiceId: 8, targetServiceName: 'Audit Logger', dependencyType: 'EVENT' },
  ];

  const mockImportedProject = {
    id: 2,
    name: 'spring-projects/spring-petclinic',
    description: 'Spring Boot PetClinic reference architecture',
    serviceCount: 3,
  };

  const mockImportedServices = [
    { id: 10, projectId: 2, projectName: 'spring-projects/spring-petclinic', name: 'OwnerController', description: 'Source: OwnerController.java:25 | Tier: CLIENT | Type: SPRING_CONTROLLER', apiCount: 5 },
    { id: 11, projectId: 2, projectName: 'spring-projects/spring-petclinic', name: 'ClinicService', description: 'Source: ClinicService.java:18 | Tier: APPLICATION | Type: SPRING_SERVICE', apiCount: 0 },
    { id: 12, projectId: 2, projectName: 'spring-projects/spring-petclinic', name: 'OwnerRepository', description: 'Source: OwnerRepository.java:14 | Tier: DOMAIN | Type: SPRING_REPOSITORY', apiCount: 0 },
  ];

  const mockImportedDependencies = [
    { id: 20, projectId: 2, sourceServiceId: 10, sourceServiceName: 'OwnerController', targetServiceId: 11, targetServiceName: 'ClinicService', dependencyType: 'INJECTION' },
    { id: 21, projectId: 2, sourceServiceId: 11, sourceServiceName: 'ClinicService', targetServiceId: 12, targetServiceName: 'OwnerRepository', dependencyType: 'INJECTION' },
  ];

  beforeEach(() => {
    mockStorage = new MockSessionStorage();
    globalThis.sessionStorage = mockStorage;
    globalThis.window = { sessionStorage: mockStorage };
    originalFetch = globalThis.fetch;

    setSession({
      token: 'jwt-token-demo-test',
      userId: '1',
      email: 'demo@impactgraph.io',
      displayName: 'Demo Engineer',
      role: 'DEVELOPER',
    });
  });

  afterEach(() => {
    clearSession();
    globalThis.fetch = originalFetch;
    delete globalThis.sessionStorage;
    delete globalThis.window;
  });

  it('1. Selecting/loading the demo produces 8 nodes and 10 edges with rich tiers and icons', async () => {
    globalThis.fetch = async (url) => {
      const u = String(url);
      if (u.includes('/api/projects/1/services')) {
        return new Response(JSON.stringify(mockDemoServices), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (u.includes('/api/projects/1/dependencies')) {
        return new Response(JSON.stringify(mockDemoDependencies), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (u.includes('/api/projects/1')) {
        return new Response(JSON.stringify(mockDemoProject), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response('Not Found', { status: 404 });
    };

    const graph = await fetchProjectGraph(1);

    assert.equal(graph.serviceCount, 8);
    assert.equal(graph.services.length, 8);
    assert.equal(graph.edgeCount, 10);
    assert.equal(graph.dependencies.length, 10);
    assert.ok(graph.name.includes('[Demo]'), `Graph name should include [Demo], was: ${graph.name}`);

    // Verify rich architectural tiers and icons
    const storefront = graph.services.find((s) => s.id === 'Storefront Web');
    assert.ok(storefront, 'Storefront Web must be present');
    assert.equal(storefront.tier, 'Client Tier');
    assert.equal(storefront.type, 'Frontend / Gateway');
    assert.equal(storefront.icon, 'globe');

    const checkout = graph.services.find((s) => s.id === 'Checkout Service');
    assert.equal(checkout.tier, 'Application Tier');
    assert.equal(checkout.icon, 'cart');

    const auth = graph.services.find((s) => s.id === 'Auth Service');
    assert.equal(auth.tier, 'Infrastructure Tier');
    assert.equal(auth.icon, 'lock');

    const payment = graph.services.find((s) => s.id === 'Payment Service');
    assert.equal(payment.tier, 'Domain Tier');
    assert.equal(payment.icon, 'card');

    const audit = graph.services.find((s) => s.id === 'Audit Logger');
    assert.equal(audit.tier, 'Security Tier');
    assert.equal(audit.icon, 'document');

    // Verify distinct tiers count
    const distinctTiers = new Set(graph.services.map((s) => s.tier));
    assert.ok(distinctTiers.size >= 5, `Expected at least 5 distinct architectural tiers, got ${distinctTiers.size}`);
  });

  it('2. Demo dependency references map to valid graph node IDs in the correct direction', async () => {
    const graph = adaptPersistedGraph(mockDemoProject, mockDemoServices, mockDemoDependencies);
    const nodeIds = new Set(graph.services.map((s) => s.id));

    assert.equal(graph.dependencies.length, 10);
    for (const dep of graph.dependencies) {
      assert.ok(
        nodeIds.has(dep.source),
        `Dependency source '${dep.source}' must exist in graph nodes`
      );
      assert.ok(
        nodeIds.has(dep.target),
        `Dependency target '${dep.target}' must exist in graph nodes`
      );
    }

    // Verify specific key dependency directions (source depends on target)
    const storefrontDep = graph.dependencies.find((d) => d.source === 'Storefront Web' && d.target === 'Checkout Service');
    assert.ok(storefrontDep, 'Storefront Web -> Checkout Service dependency must exist');

    const checkoutOrderDep = graph.dependencies.find((d) => d.source === 'Checkout Service' && d.target === 'Order Service');
    assert.ok(checkoutOrderDep, 'Checkout Service -> Order Service dependency must exist');

    const orderPaymentDep = graph.dependencies.find((d) => d.source === 'Order Service' && d.target === 'Payment Service');
    assert.ok(orderPaymentDep, 'Order Service -> Payment Service dependency must exist');
  });

  it('3. Switching from an imported repository back to the demo renders the demo graph correctly', async () => {
    // 3a. Adapt imported repository
    const importedGraph = adaptPersistedGraph(mockImportedProject, mockImportedServices, mockImportedDependencies);
    assert.equal(importedGraph.serviceCount, 3);
    assert.equal(importedGraph.edgeCount, 2);
    assert.equal(importedGraph.name, 'spring-projects/spring-petclinic');
    assert.equal(importedGraph.services[0].id, 'OwnerController');
    assert.equal(importedGraph.services[0].tier, 'Client Tier');

    // 3b. Switch back to demo project
    const demoGraph = adaptPersistedGraph(mockDemoProject, mockDemoServices, mockDemoDependencies);
    assert.equal(demoGraph.serviceCount, 8);
    assert.equal(demoGraph.edgeCount, 10);
    assert.ok(demoGraph.name.includes('[Demo]'));
    assert.ok(demoGraph.services.some((s) => s.id === 'Payment Service'));
    assert.ok(demoGraph.services.some((s) => s.id === 'Storefront Web'));
  });

  it('4. Blast-radius simulation for Payment Service correctly identifies affected components and paths', async () => {
    const mockImpactResponse = {
      changedServiceId: 'Payment Service',
      affectedCount: 3,
      affectedServiceIds: ['Checkout Service', 'Order Service', 'Storefront Web'],
      directAffectedServices: ['Checkout Service', 'Order Service'],
      transitiveAffectedServices: ['Storefront Web'],
      impactPaths: [
        {
          affectedServiceId: 'Checkout Service',
          changedServiceId: 'Payment Service',
          distance: 1,
          isDirect: true,
          isTransitive: false,
          path: ['Checkout Service', 'Payment Service'],
          pathString: 'Checkout Service -> Payment Service',
        },
        {
          affectedServiceId: 'Order Service',
          changedServiceId: 'Payment Service',
          distance: 1,
          isDirect: true,
          isTransitive: false,
          path: ['Order Service', 'Payment Service'],
          pathString: 'Order Service -> Payment Service',
        },
        {
          affectedServiceId: 'Storefront Web',
          changedServiceId: 'Payment Service',
          distance: 2,
          isDirect: false,
          isTransitive: true,
          path: ['Storefront Web', 'Checkout Service', 'Payment Service'],
          pathString: 'Storefront Web -> Checkout Service -> Payment Service',
        },
      ],
      cycleResult: { hasCycle: false, cycleCount: 0, cycles: [] },
    };

    globalThis.fetch = async (url) => {
      const u = String(url);
      if (u.includes('/api/impact/Payment%20Service') || u.includes('/api/impact/Payment Service')) {
        return new Response(JSON.stringify(mockImpactResponse), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response('Not Found', { status: 404 });
    };

    const impact = await analyzeChangeImpact('Payment Service', 'ecommerce-dag', { projectId: 1 });

    assert.equal(impact.changedServiceId, 'Payment Service');
    assert.equal(impact.affectedCount, 3);
    assert.deepEqual(impact.directAffectedServices, ['Checkout Service', 'Order Service']);
    assert.deepEqual(impact.transitiveAffectedServices, ['Storefront Web']);
    assert.equal(impact.impactPaths.length, 3);

    const directPath = impact.impactPaths.find((p) => p.affectedServiceId === 'Checkout Service');
    assert.equal(directPath.distance, 1);
    assert.equal(directPath.isDirect, true);

    const transitivePath = impact.impactPaths.find((p) => p.affectedServiceId === 'Storefront Web');
    assert.equal(transitivePath.distance, 2);
    assert.equal(transitivePath.isTransitive, true);
  });

  it('5. In-memory demo preset adaptation preserves [Demo] naming and full 8-service topology', () => {
    const rawPreset = {
      preset: 'ecommerce-dag',
      name: 'E-Commerce Architecture',
      description: 'Standard 8-service microservices topology',
      services: Object.keys(DEMO_SERVICE_METADATA).map((name) => ({
        id: name,
        label: name,
        ...DEMO_SERVICE_METADATA[name],
      })),
      dependencies: mockDemoDependencies.map((d) => ({
        source: d.sourceServiceName,
        target: d.targetServiceName,
      })),
    };

    const adapted = adaptGraphResponse(rawPreset);
    assert.ok(adapted.name.includes('[Demo]'), `Preset graph name should include [Demo], was ${adapted.name}`);
    assert.equal(adapted.services.length, 8);
    assert.equal(adapted.dependencies.length, 10);
    assert.equal(adapted.preset, 'ecommerce-dag');
  });

  it('6. resolveServiceMetadata handles both standard demo components and AST parsed repository services', () => {
    // 6a. Standard demo service
    const metaDemo = resolveServiceMetadata({ name: 'Checkout Service' });
    assert.equal(metaDemo.tier, 'Application Tier');
    assert.equal(metaDemo.type, 'Orchestrator');
    assert.equal(metaDemo.icon, 'cart');

    // 6b. AST parsed Spring Controller
    const metaController = resolveServiceMetadata({
      name: 'PaymentController',
      description: 'Source: PaymentController.java:12 | Tier: CLIENT | Type: SPRING_CONTROLLER',
    });
    assert.equal(metaController.tier, 'Client Tier');
    assert.equal(metaController.type, 'Spring Controller');
    assert.equal(metaController.icon, 'globe');

    // 6c. AST parsed Spring Service
    const metaService = resolveServiceMetadata({
      name: 'OrderProcessor',
      description: 'Source: OrderProcessor.java:45 | Tier: DOMAIN | Type: SPRING_SERVICE',
    });
    assert.equal(metaService.tier, 'Domain Tier');
    assert.equal(metaService.type, 'Spring Service');
  });
});
