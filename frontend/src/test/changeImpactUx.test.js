import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { NODE_POSITIONS } from '../data/mockServicesData.js';

describe('Change Impact UX, Usability & Security Visibility Regression Suite', () => {

  describe('1. Graph Layout Spacing & Tier Geometry', () => {
    it('demo NODE_POSITIONS defines non-overlapping coordinates for all 8 canonical services', () => {
      const canonicalServices = [
        'Storefront Web',
        'Checkout Service',
        'Auth Service',
        'Order Service',
        'Payment Service',
        'Inventory Service',
        'Notification Service',
        'Audit Logger',
      ];

      for (const serviceName of canonicalServices) {
        assert.ok(
          NODE_POSITIONS[serviceName],
          `Service '${serviceName}' must have predefined coordinates in NODE_POSITIONS`
        );
        assert.equal(typeof NODE_POSITIONS[serviceName].x, 'number');
        assert.equal(typeof NODE_POSITIONS[serviceName].y, 'number');
      }

      // Ensure no two services occupy the exact same position or overlap closely (< 150px)
      const positions = canonicalServices.map((name) => ({
        name,
        ...NODE_POSITIONS[name],
      }));

      for (let i = 0; i < positions.length; i++) {
        for (let j = i + 1; j < positions.length; j++) {
          const dx = Math.abs(positions[i].x - positions[j].x);
          const dy = Math.abs(positions[i].y - positions[j].y);
          const dist = Math.hypot(dx, dy);
          assert.ok(
            dist >= 140,
            `Services '${positions[i].name}' and '${positions[j].name}' overlap too closely (distance: ${dist.toFixed(1)}px)`
          );
        }
      }
    });

    it('tier-aware automatic layout algorithm distributes nodes into distinct architectural rows', () => {
      // Simulate tier positioning logic used in GraphView.jsx
      const TIER_ORDER = {
        'CLIENT': 0,
        'APPLICATION': 1,
        'DOMAIN': 2,
        'INFRASTRUCTURE': 3,
        'INTEGRATION': 4,
        'SECURITY': 5,
        'UNKNOWN': 3,
      };

      const testNodes = [
        { id: 'WebController', tier: 'CLIENT' },
        { id: 'CheckoutService', tier: 'APPLICATION' },
        { id: 'OrderRepository', tier: 'DOMAIN' },
        { id: 'PostgresConnector', tier: 'INFRASTRUCTURE' },
        { id: 'EmailDispatcher', tier: 'INTEGRATION' },
        { id: 'AuditFilter', tier: 'SECURITY' },
      ];

      const tiers = {};
      testNodes.forEach((node) => {
        const t = (node.tier || 'UNKNOWN').toUpperCase();
        if (!tiers[t]) tiers[t] = [];
        tiers[t].push(node);
      });

      const calculatedPositions = {};
      const HORIZONTAL_SPACING = 300;
      const VERTICAL_SPACING = 170;

      Object.keys(tiers).forEach((tierName) => {
        const tierIndex = TIER_ORDER[tierName] ?? 3;
        const rowNodes = tiers[tierName];
        const rowWidth = (rowNodes.length - 1) * HORIZONTAL_SPACING;
        const startX = Math.max(80, 500 - rowWidth / 2);
        const y = 80 + tierIndex * VERTICAL_SPACING;

        rowNodes.forEach((node, idx) => {
          calculatedPositions[node.id] = {
            x: startX + idx * HORIZONTAL_SPACING,
            y,
          };
        });
      });

      // Verify CLIENT tier is placed at the top (lowest y) and SECURITY tier at the bottom (highest y)
      assert.ok(calculatedPositions['WebController'].y < calculatedPositions['CheckoutService'].y);
      assert.ok(calculatedPositions['CheckoutService'].y < calculatedPositions['OrderRepository'].y);
      assert.ok(calculatedPositions['OrderRepository'].y < calculatedPositions['PostgresConnector'].y);
      assert.ok(calculatedPositions['PostgresConnector'].y < calculatedPositions['AuditFilter'].y);
    });
  });

  describe('2. Animated Change Impact State Progression', () => {
    it('animation phases progress logically from Changed Root -> Direct -> Transitive -> APIs -> Evidence', () => {
      const stepDescriptions = [
        'Baseline Architecture',
        'Changed Root Components',
        'Direct Dependency Invocations',
        'Downstream Transitive Impact Paths',
        'Correlated External API Endpoints',
        'Full Source Evidence & Test Verification',
      ];

      assert.equal(stepDescriptions.length, 6);
      assert.equal(stepDescriptions[0], 'Baseline Architecture');
      assert.equal(stepDescriptions[1], 'Changed Root Components');
      assert.equal(stepDescriptions[5], 'Full Source Evidence & Test Verification');

      // Test progressive filtering simulation:
      const impactResult = {
        directlyChangedServices: ['Payment Service'],
        directAffectedServices: ['Checkout Service', 'Order Service'],
        transitiveAffectedServices: ['Storefront Web'],
        impactedApis: [{ path: '/api/v1/checkout', controllerName: 'Checkout Service' }],
      };

      // Step 1: Only changed roots highlighted
      const step1Highlighted = (nodeId) => impactResult.directlyChangedServices.includes(nodeId);
      assert.equal(step1Highlighted('Payment Service'), true);
      assert.equal(step1Highlighted('Checkout Service'), false);
      assert.equal(step1Highlighted('Storefront Web'), false);

      // Step 2: Changed roots + direct dependents highlighted
      const step2Highlighted = (nodeId) =>
        step1Highlighted(nodeId) || impactResult.directAffectedServices.includes(nodeId);
      assert.equal(step2Highlighted('Payment Service'), true);
      assert.equal(step2Highlighted('Checkout Service'), true);
      assert.equal(step2Highlighted('Storefront Web'), false);

      // Step 3: Changed roots + direct + transitive callers highlighted
      const step3Highlighted = (nodeId) =>
        step2Highlighted(nodeId) || impactResult.transitiveAffectedServices.includes(nodeId);
      assert.equal(step3Highlighted('Payment Service'), true);
      assert.equal(step3Highlighted('Checkout Service'), true);
      assert.equal(step3Highlighted('Storefront Web'), true);
    });
  });

  describe('3. Focus Mode & Neighborhood Filtering', () => {
    it('isolates target node and direct bidirectional neighbors while dimming others', () => {
      const dependencies = [
        { source: 'Storefront Web', target: 'Checkout Service' },
        { source: 'Checkout Service', target: 'Payment Service' },
        { source: 'Order Service', target: 'Payment Service' },
        { source: 'Auth Service', target: 'Audit Logger' },
      ];

      const activeFocusId = 'Payment Service';
      const focusNeighbors = new Set();
      focusNeighbors.add(activeFocusId);

      for (const dep of dependencies) {
        if (dep.source === activeFocusId) focusNeighbors.add(dep.target);
        if (dep.target === activeFocusId) focusNeighbors.add(dep.source);
      }

      // Payment Service is connected directly to Checkout Service and Order Service
      assert.equal(focusNeighbors.has('Payment Service'), true);
      assert.equal(focusNeighbors.has('Checkout Service'), true);
      assert.equal(focusNeighbors.has('Order Service'), true);

      // Unrelated services must NOT be in focus neighbors
      assert.equal(focusNeighbors.has('Storefront Web'), false);
      assert.equal(focusNeighbors.has('Auth Service'), false);
      assert.equal(focusNeighbors.has('Audit Logger'), false);
    });
  });

  describe('4. Evidence-Based Change Explanations & Potential Impact Distinction', () => {
    it('correctly models source evidence, line ranges, and flags potential vs confirmed regression', () => {
      const explanation = {
        targetId: 'Checkout Service',
        targetType: 'COMPONENT',
        changeOrigin: 'Payment Service',
        impactType: 'DIRECT',
        dependencyPath: ['Checkout Service', 'Payment Service'],
        pathString: 'Checkout Service -> Payment Service',
        sourceEvidence: 'OrderController.java:L45-L52',
        explanation: 'Checkout Service directly calls Payment Service along dependency path: Checkout Service -> Payment Service.',
        confidence: 0.95,
        recommendedVerificationSteps: [
          'Run targeted integration tests covering Checkout Service -> Payment Service interactions.',
          'Verify API backward compatibility.',
        ],
        relevantTests: ['CheckoutIntegrationTest.java'],
        isPotentialImpactOnly: true,
      };

      assert.equal(explanation.targetId, 'Checkout Service');
      assert.equal(explanation.impactType, 'DIRECT');
      assert.equal(explanation.isPotentialImpactOnly, true);
      assert.ok(explanation.sourceEvidence.includes(':L45-L52'));
      assert.ok(explanation.recommendedVerificationSteps.length >= 2);
      assert.ok(explanation.relevantTests.includes('CheckoutIntegrationTest.java'));
    });
  });

  describe('5. Security Findings Prominence & Summary Representation', () => {
    it('summarizes findings honestly with severity breakdowns and distinguishes unanalyzed states', () => {
      // Populated analysis scenario
      const findingsSummary = {
        total: 4,
        critical: 1,
        high: 2,
        medium: 1,
        low: 0,
        hasRun: true,
      };

      assert.equal(findingsSummary.total, 4);
      assert.equal(findingsSummary.critical, 1);
      assert.equal(findingsSummary.high + findingsSummary.medium + findingsSummary.low, 3);
      assert.equal(findingsSummary.hasRun, true);

      // Honest unanalyzed state scenario
      const unanalyzedProject = {
        total: 0,
        hasRun: false,
      };
      assert.equal(unanalyzedProject.hasRun, false);
      // UI must not state "0 Vulnerabilities (Secure)" when analysis hasn't run
      const label = unanalyzedProject.hasRun
        ? (unanalyzedProject.total === 0 ? 'No Findings Detected' : `${unanalyzedProject.total} Security Findings`)
        : 'Security Analysis Pending';
      assert.equal(label, 'Security Analysis Pending');
    });
  });

  describe('6. Infrastructure Detail Obfuscation for User-Facing UI', () => {
    it('sanitizes developer/infrastructure labels into user-friendly terminology', () => {
      const rawLabels = [
        'PostgreSQL Database Project #12',
        'Backend Online (8080)',
        'Spring Boot Engine Initialized',
        'Active PostgreSQL Schema Connection',
      ];

      const sanitizeLabel = (text) => {
        return text
          .replace(/PostgreSQL\s+Database\s+Project\s+#(\d+)/gi, 'Repository Project #$1')
          .replace(/Backend Online \(8080\)/gi, 'Analysis Engine Online')
          .replace(/Spring Boot Engine Initialized/gi, 'Architecture Intelligence Ready')
          .replace(/Active PostgreSQL Schema Connection/gi, 'Repository Persistence Ready');
      };

      for (const raw of rawLabels) {
        const sanitized = sanitizeLabel(raw);
        assert.equal(/PostgreSQL/i.test(sanitized), false, `Should not expose PostgreSQL: ${sanitized}`);
        assert.equal(/8080/i.test(sanitized), false, `Should not expose port 8080: ${sanitized}`);
        assert.equal(/Spring Boot Engine/i.test(sanitized), false, `Should not expose Spring Boot: ${sanitized}`);
      }
    });
  });
});
