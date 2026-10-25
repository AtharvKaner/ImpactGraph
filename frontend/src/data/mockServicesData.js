/**
 * Mock datasets for ImpactGraph Phase 2A Frontend Demonstration.
 *
 * NOTE: This data is statically defined in-memory for the frontend demonstration.
 * It is NOT fetched from the PostgreSQL database or backend REST APIs (pending Phase 2B).
 *
 * Direction Convention:
 * A -> B means A depends on B (A is dependent, B is dependency).
 * When B changes, dependents are found by following reverse/incoming edges.
 */

export const DATASET_PRESETS = {
  ECOMMERCE_DAG: 'ecommerce-dag',
  CIRCULAR_DEMO: 'circular-demo',
};

// Preset 1: Standard E-Commerce Microservices Architecture (Acyclic DAG)
const ECOMMERCE_SERVICES = [
  { id: 'Storefront Web', label: 'Storefront Web', type: 'Frontend / Gateway', tier: 'Client Tier', icon: 'globe' },
  { id: 'Checkout Service', label: 'Checkout Service', type: 'Orchestrator', tier: 'Application Tier', icon: 'cart' },
  { id: 'Auth Service', label: 'Auth Service', type: 'Security / Identity', tier: 'Infrastructure Tier', icon: 'lock' },
  { id: 'Order Service', label: 'Order Service', type: 'Core Domain', tier: 'Domain Tier', icon: 'database' },
  { id: 'Payment Service', label: 'Payment Service', type: 'Core Domain', tier: 'Domain Tier', icon: 'card' },
  { id: 'Inventory Service', label: 'Inventory Service', type: 'Core Domain', tier: 'Domain Tier', icon: 'cube' },
  { id: 'Notification Service', label: 'Notification Service', type: 'Messaging / Events', tier: 'Integration Tier', icon: 'bell' },
  { id: 'Audit Logger', label: 'Audit Logger', type: 'Compliance / CNS', tier: 'Security Tier', icon: 'document' },
];

// Convention: source -> target means source depends on target
const ECOMMERCE_DEPENDENCIES = [
  { source: 'Storefront Web', target: 'Checkout Service' },
  { source: 'Storefront Web', target: 'Auth Service' },
  { source: 'Checkout Service', target: 'Order Service' },
  { source: 'Checkout Service', target: 'Payment Service' },
  { source: 'Order Service', target: 'Payment Service' },
  { source: 'Order Service', target: 'Inventory Service' },
  { source: 'Order Service', target: 'Audit Logger' },
  { source: 'Payment Service', target: 'Notification Service' },
  { source: 'Payment Service', target: 'Audit Logger' },
  { source: 'Auth Service', target: 'Audit Logger' },
];

// Layout positions for React Flow nodes matching the reference design
export const NODE_POSITIONS = {
  'Storefront Web': { x: 270, y: 30 },
  'Checkout Service': { x: 270, y: 150 },
  'Auth Service': { x: 50, y: 280 },
  'Order Service': { x: 270, y: 280 },
  'Payment Service': { x: 490, y: 280 },
  'Inventory Service': { x: 50, y: 420 },
  'Notification Service': { x: 270, y: 420 },
  'Audit Logger': { x: 490, y: 420 },
};

// Preset 2: Circular Dependency Example
const CIRCULAR_DEPENDENCIES = [
  ...ECOMMERCE_DEPENDENCIES,
  // Add circular edge: Notification Service depends on Payment Service
  // creating Payment Service -> Notification Service -> Payment Service
  { source: 'Notification Service', target: 'Payment Service' },
];

/**
 * Pre-computed impact analysis results conforming exactly to the backend
 * com.impactgraph.graph.ImpactAnalysisResult and CycleDetectionResult models.
 */
const ECOMMERCE_ANALYSIS_MAP = {
  'Payment Service': {
    changedServiceId: 'Payment Service',
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
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Order Service': {
    changedServiceId: 'Order Service',
    affectedServiceIds: ['Checkout Service', 'Storefront Web'],
    directAffectedServices: ['Checkout Service'],
    transitiveAffectedServices: ['Storefront Web'],
    impactPaths: [
      {
        affectedServiceId: 'Checkout Service',
        changedServiceId: 'Order Service',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Checkout Service', 'Order Service'],
        pathString: 'Checkout Service -> Order Service',
      },
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Order Service',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Storefront Web', 'Checkout Service', 'Order Service'],
        pathString: 'Storefront Web -> Checkout Service -> Order Service',
      },
    ],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Inventory Service': {
    changedServiceId: 'Inventory Service',
    affectedServiceIds: ['Checkout Service', 'Order Service', 'Storefront Web'],
    directAffectedServices: ['Order Service'],
    transitiveAffectedServices: ['Checkout Service', 'Storefront Web'],
    impactPaths: [
      {
        affectedServiceId: 'Order Service',
        changedServiceId: 'Inventory Service',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Order Service', 'Inventory Service'],
        pathString: 'Order Service -> Inventory Service',
      },
      {
        affectedServiceId: 'Checkout Service',
        changedServiceId: 'Inventory Service',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Checkout Service', 'Order Service', 'Inventory Service'],
        pathString: 'Checkout Service -> Order Service -> Inventory Service',
      },
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Inventory Service',
        distance: 3,
        isDirect: false,
        isTransitive: true,
        path: ['Storefront Web', 'Checkout Service', 'Order Service', 'Inventory Service'],
        pathString: 'Storefront Web -> Checkout Service -> Order Service -> Inventory Service',
      },
    ],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Notification Service': {
    changedServiceId: 'Notification Service',
    affectedServiceIds: ['Checkout Service', 'Order Service', 'Payment Service', 'Storefront Web'],
    directAffectedServices: ['Payment Service'],
    transitiveAffectedServices: ['Checkout Service', 'Order Service', 'Storefront Web'],
    impactPaths: [
      {
        affectedServiceId: 'Payment Service',
        changedServiceId: 'Notification Service',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Payment Service', 'Notification Service'],
        pathString: 'Payment Service -> Notification Service',
      },
      {
        affectedServiceId: 'Checkout Service',
        changedServiceId: 'Notification Service',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Checkout Service', 'Payment Service', 'Notification Service'],
        pathString: 'Checkout Service -> Payment Service -> Notification Service',
      },
      {
        affectedServiceId: 'Order Service',
        changedServiceId: 'Notification Service',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Order Service', 'Payment Service', 'Notification Service'],
        pathString: 'Order Service -> Payment Service -> Notification Service',
      },
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Notification Service',
        distance: 3,
        isDirect: false,
        isTransitive: true,
        path: ['Storefront Web', 'Checkout Service', 'Payment Service', 'Notification Service'],
        pathString: 'Storefront Web -> Checkout Service -> Payment Service -> Notification Service',
      },
    ],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Audit Logger': {
    changedServiceId: 'Audit Logger',
    affectedServiceIds: ['Auth Service', 'Checkout Service', 'Order Service', 'Payment Service', 'Storefront Web'],
    directAffectedServices: ['Auth Service', 'Order Service', 'Payment Service'],
    transitiveAffectedServices: ['Checkout Service', 'Storefront Web'],
    impactPaths: [
      {
        affectedServiceId: 'Auth Service',
        changedServiceId: 'Audit Logger',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Auth Service', 'Audit Logger'],
        pathString: 'Auth Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Order Service',
        changedServiceId: 'Audit Logger',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Order Service', 'Audit Logger'],
        pathString: 'Order Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Payment Service',
        changedServiceId: 'Audit Logger',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Payment Service', 'Audit Logger'],
        pathString: 'Payment Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Checkout Service',
        changedServiceId: 'Audit Logger',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Checkout Service', 'Order Service', 'Audit Logger'],
        pathString: 'Checkout Service -> Order Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Audit Logger',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Storefront Web', 'Auth Service', 'Audit Logger'],
        pathString: 'Storefront Web -> Auth Service -> Audit Logger',
      },
    ],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Auth Service': {
    changedServiceId: 'Auth Service',
    affectedServiceIds: ['Storefront Web'],
    directAffectedServices: ['Storefront Web'],
    transitiveAffectedServices: [],
    impactPaths: [
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Auth Service',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Storefront Web', 'Auth Service'],
        pathString: 'Storefront Web -> Auth Service',
      },
    ],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Checkout Service': {
    changedServiceId: 'Checkout Service',
    affectedServiceIds: ['Storefront Web'],
    directAffectedServices: ['Storefront Web'],
    transitiveAffectedServices: [],
    impactPaths: [
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Checkout Service',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Storefront Web', 'Checkout Service'],
        pathString: 'Storefront Web -> Checkout Service',
      },
    ],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
  'Storefront Web': {
    changedServiceId: 'Storefront Web',
    affectedServiceIds: [],
    directAffectedServices: [],
    transitiveAffectedServices: [],
    impactPaths: [],
    cycleResult: {
      hasCycle: false,
      cycles: [],
    },
  },
};

/**
 * Retrieves the services and dependencies for a chosen dataset preset.
 */
export function getDataset(preset = DATASET_PRESETS.ECOMMERCE_DAG) {
  if (preset === DATASET_PRESETS.CIRCULAR_DEMO) {
    return {
      preset,
      name: 'Circular Feedback Demo',
      description: 'Demonstrates circular dependency detection (Payment <-> Notification)',
      services: ECOMMERCE_SERVICES,
      dependencies: CIRCULAR_DEPENDENCIES,
      hasCycle: true,
      cycleDetails: [
        {
          nodes: ['Payment Service', 'Notification Service', 'Payment Service'],
          cycleString: 'Payment Service -> Notification Service -> Payment Service',
          length: 2,
        },
      ],
    };
  }

  return {
    preset: DATASET_PRESETS.ECOMMERCE_DAG,
    name: 'E-Commerce Architecture',
    description: 'Standard 8-service microservices topology (Acyclic DAG)',
    services: ECOMMERCE_SERVICES,
    dependencies: ECOMMERCE_DEPENDENCIES,
    hasCycle: false,
    cycleDetails: [],
  };
}

/**
 * Pre-computed overrides for the circular-demo preset where the Notification Service -> Payment Service
 * edge introduces circular feedback and changes the affected dependents for Payment Service and Audit Logger.
 */
const CIRCULAR_ANALYSIS_OVERRIDES = {
  'Payment Service': {
    changedServiceId: 'Payment Service',
    affectedServiceIds: ['Checkout Service', 'Notification Service', 'Order Service', 'Storefront Web'],
    directAffectedServices: ['Checkout Service', 'Notification Service', 'Order Service'],
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
        affectedServiceId: 'Notification Service',
        changedServiceId: 'Payment Service',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Notification Service', 'Payment Service'],
        pathString: 'Notification Service -> Payment Service',
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
    cycleResult: {
      hasCycle: true,
      cycles: [
        {
          nodes: ['Payment Service', 'Notification Service', 'Payment Service'],
          cycleString: 'Payment Service -> Notification Service -> Payment Service',
          length: 2,
        },
      ],
    },
  },
  'Audit Logger': {
    changedServiceId: 'Audit Logger',
    affectedServiceIds: ['Auth Service', 'Checkout Service', 'Notification Service', 'Order Service', 'Payment Service', 'Storefront Web'],
    directAffectedServices: ['Auth Service', 'Order Service', 'Payment Service'],
    transitiveAffectedServices: ['Checkout Service', 'Notification Service', 'Storefront Web'],
    impactPaths: [
      {
        affectedServiceId: 'Auth Service',
        changedServiceId: 'Audit Logger',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Auth Service', 'Audit Logger'],
        pathString: 'Auth Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Order Service',
        changedServiceId: 'Audit Logger',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Order Service', 'Audit Logger'],
        pathString: 'Order Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Payment Service',
        changedServiceId: 'Audit Logger',
        distance: 1,
        isDirect: true,
        isTransitive: false,
        path: ['Payment Service', 'Audit Logger'],
        pathString: 'Payment Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Checkout Service',
        changedServiceId: 'Audit Logger',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Checkout Service', 'Order Service', 'Audit Logger'],
        pathString: 'Checkout Service -> Order Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Notification Service',
        changedServiceId: 'Audit Logger',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Notification Service', 'Payment Service', 'Audit Logger'],
        pathString: 'Notification Service -> Payment Service -> Audit Logger',
      },
      {
        affectedServiceId: 'Storefront Web',
        changedServiceId: 'Audit Logger',
        distance: 2,
        isDirect: false,
        isTransitive: true,
        path: ['Storefront Web', 'Auth Service', 'Audit Logger'],
        pathString: 'Storefront Web -> Auth Service -> Audit Logger',
      },
    ],
    cycleResult: {
      hasCycle: true,
      cycles: [
        {
          nodes: ['Payment Service', 'Notification Service', 'Payment Service'],
          cycleString: 'Payment Service -> Notification Service -> Payment Service',
          length: 2,
        },
      ],
    },
  },
};

/**
 * Returns pre-computed impact analysis result for a given service.
 */
export function getMockImpactResult(serviceId, preset = DATASET_PRESETS.ECOMMERCE_DAG) {
  if (preset === DATASET_PRESETS.CIRCULAR_DEMO && CIRCULAR_ANALYSIS_OVERRIDES[serviceId]) {
    return CIRCULAR_ANALYSIS_OVERRIDES[serviceId];
  }

  const baseResult = ECOMMERCE_ANALYSIS_MAP[serviceId] || {
    changedServiceId: serviceId,
    affectedServiceIds: [],
    directAffectedServices: [],
    transitiveAffectedServices: [],
    impactPaths: [],
    cycleResult: { hasCycle: false, cycles: [] },
  };

  if (preset === DATASET_PRESETS.CIRCULAR_DEMO) {
    return {
      ...baseResult,
      cycleResult: {
        hasCycle: true,
        cycles: [
          {
            nodes: ['Payment Service', 'Notification Service', 'Payment Service'],
            cycleString: 'Payment Service -> Notification Service -> Payment Service',
            length: 2,
          },
        ],
      },
    };
  }

  return baseResult;
}
