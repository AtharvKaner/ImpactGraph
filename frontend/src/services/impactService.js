/**
 * ImpactGraph Analysis Service Layer.
 *
 * Connects the React dashboard to the live Spring Boot backend REST API
 * while retaining robust offline demonstration fallback when the backend
 * is unreachable.
 *
 * Invariant Rules:
 * 1. Live backend data is the primary source when reachable.
 * 2. Demonstration data is used ONLY when a genuine network or connectivity
 *    failure occurs (e.g. connection refused, network timeout).
 * 3. Server HTTP errors (400 Bad Request, 404 Not Found, 500) are NEVER
 *    silently replaced with mock data; they are propagated as explicit errors.
 * 4. Response semantics preserve:
 *    - source is dependent, target is dependency (A -> B means A depends on B).
 *    - Direct & transitive affected services come from backend analysis.
 *    - Ordered impact paths and hop distances are preserved.
 *    - Cycle status and cycle nodes come from backend cycle detector.
 */

import {
  DATASET_PRESETS,
  getDataset,
  getMockImpactResult,
} from '../data/mockServicesData.js';

export { DATASET_PRESETS };

/**
 * Base URL for the Spring Boot backend API.
 * Uses VITE_API_BASE_URL if configured, otherwise falls back to '' (relative)
 * which routes through the Vite development proxy (`/api` -> `http://localhost:8080`).
 */
export const API_BASE_URL = typeof import.meta !== 'undefined' && import.meta?.env?.VITE_API_BASE_URL !== undefined
  ? import.meta.env.VITE_API_BASE_URL
  : '';

/**
 * Error class representing an HTTP error response from the backend.
 */
export class HttpError extends Error {
  constructor(status, statusText, message, body = null) {
    super(message || `Backend responded with HTTP ${status} (${statusText})`);
    this.name = 'HttpError';
    this.status = status;
    this.statusText = statusText;
    this.isHttpError = true;
    this.body = body;
  }
}

/**
 * Helper to perform GET requests with timeout, signal cancellation, and clean error parsing.
 *
 * @param {string} path API path (e.g. '/api/graph')
 * @param {object} options request options including signal and timeout (ms)
 * @returns {Promise<any>} parsed JSON response
 */
async function apiGet(path, { timeout = 5000, signal: externalSignal, headers = {} } = {}) {
  const controller = new AbortController();
  let timedOut = false;

  const timeoutId = setTimeout(() => {
    timedOut = true;
    controller.abort(new Error(`Request to ${path} timed out after ${timeout}ms`));
  }, timeout);

  const abortHandler = () => {
    controller.abort(externalSignal?.reason);
  };

  if (externalSignal) {
    if (externalSignal.aborted) {
      clearTimeout(timeoutId);
      controller.abort(externalSignal.reason);
    } else {
      externalSignal.addEventListener('abort', abortHandler, { once: true });
    }
  }

  const base = API_BASE_URL.replace(/\/+$/, '');
  const cleanPath = path.startsWith('/') ? path : `/${path}`;
  const url = `${base}${cleanPath}`;

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        ...headers,
      },
      signal: controller.signal,
    });

    if (!response.ok) {
      let errorBody = null;
      try {
        errorBody = await response.json();
      } catch {
        // Non-JSON response
      }
      const message = errorBody?.message || `Backend returned HTTP ${response.status} (${response.statusText})`;
      throw new HttpError(response.status, response.statusText, message, errorBody);
    }

    return await response.json();
  } catch (err) {
    if (timedOut) {
      const timeoutErr = new Error(`Request to backend (${cleanPath}) timed out after ${timeout}ms`);
      timeoutErr.name = 'TimeoutError';
      throw timeoutErr;
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
    if (externalSignal) {
      externalSignal.removeEventListener('abort', abortHandler);
    }
  }
}

/**
 * Checks whether an error represents a genuine network / connectivity failure
 * (e.g. server offline, connection refused, DNS failure, timeout) as opposed
 * to an HTTP error returned by the server (e.g. 400 Bad Request, 404 Not Found).
 *
 * @param {any} err
 * @returns {boolean}
 */
export function isNetworkFailure(err) {
  if (!err) return false;
  // If the server answered with an HTTP error code, it is NOT a network outage
  if (err.isHttpError || err instanceof HttpError) {
    return false;
  }
  // User-initiated cancellations shouldn't be counted as network outages
  if (err.name === 'AbortError' && err.message?.includes('aborted')) {
    return false;
  }
  return true;
}

/**
 * Adapts backend GraphResponseDto or mock dataset into the UI shape.
 */
export function adaptGraphResponse(data, { isLive = true, isFallback = false, fallbackReason = null } = {}) {
  const cycles = data.cycles || data.cycleDetails || [];
  return {
    preset: data.preset || DATASET_PRESETS.ECOMMERCE_DAG,
    name: data.name || 'Demonstration Topology',
    description: data.description || '',
    serviceCount: data.serviceCount ?? (data.services || []).length,
    edgeCount: data.edgeCount ?? (data.dependencies || []).length,
    hasCycle: Boolean(data.hasCycle),
    services: data.services || [],
    dependencies: (data.dependencies || []).map((dep) => ({
      source: dep.source || dep.dependentId,
      target: dep.target || dep.dependencyId,
      dependentId: dep.dependentId || dep.source,
      dependencyId: dep.dependencyId || dep.target,
    })),
    cycles,
    cycleDetails: cycles,
    isLive,
    isMock: !isLive,
    isFallback,
    fallbackReason,
    sourceNote: isLive
      ? 'Live Spring Boot Backend (Port 8080)'
      : 'In-Memory Client Mock (Offline Fallback)',
  };
}

/**
 * Adapts backend ImpactAnalysisResponseDto or mock impact result into the UI shape.
 */
export function adaptImpactResponse(data, { isLive = true, isFallback = false, fallbackReason = null } = {}) {
  if (!data) return null;

  const rawPaths = data.impactPaths || [];
  const normalizedPaths = rawPaths.map((p) => ({
    affectedServiceId: p.affectedServiceId,
    changedServiceId: p.changedServiceId,
    distance: p.distance ?? (p.path ? p.path.length - 1 : 1),
    isDirect: Boolean(p.isDirect ?? (p.distance === 1)),
    isTransitive: Boolean(p.isTransitive ?? (p.distance > 1)),
    path: p.path || [],
    pathString: p.pathString || (p.path ? p.path.join(' -> ') : ''),
  }));

  const cycleResult = {
    hasCycle: Boolean(data.cycleResult?.hasCycle),
    cycleCount: data.cycleResult?.cycleCount ?? (data.cycleResult?.cycles || []).length,
    cycles: data.cycleResult?.cycles || [],
  };

  return {
    changedServiceId: data.changedServiceId,
    affectedCount: data.affectedCount ?? (data.affectedServiceIds || []).length,
    affectedServiceIds: data.affectedServiceIds || [],
    directAffectedServices: data.directAffectedServices || [],
    transitiveAffectedServices: data.transitiveAffectedServices || [],
    impactPaths: normalizedPaths,
    cycleResult,
    isLive,
    isMock: !isLive,
    isFallback,
    fallbackReason,
    sourceNote: isLive
      ? 'Live Spring Boot Backend (Port 8080)'
      : 'In-Memory Client Mock (Offline Fallback)',
  };
}

/**
 * Fetches the backend health check status.
 *
 * @param {object} options
 * @returns {Promise<{ status: string, service: string, timestamp: string }>}
 */
export async function checkBackendHealth(options = {}) {
  return await apiGet('/api/health', options);
}

/**
 * Fetches the list of available services for a preset.
 *
 * @param {string} preset
 * @param {object} options
 * @returns {Promise<Array<object>>}
 */
export async function fetchServices(preset = DATASET_PRESETS.ECOMMERCE_DAG, options = {}) {
  try {
    const data = await apiGet(`/api/services?preset=${encodeURIComponent(preset)}`, options);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    if (!isNetworkFailure(err)) {
      throw err;
    }
    console.warn(`[impactService] Live backend unreachable (${err.message}). Using fallback services.`);
    const fallbackDataset = getDataset(preset);
    return fallbackDataset.services || [];
  }
}

/**
 * Fetches the graph topology (nodes, edges, cycle status) for a preset.
 *
 * @param {string} preset
 * @param {object} options
 * @returns {Promise<object>} adapted graph topology
 */
export async function fetchServiceGraph(preset = DATASET_PRESETS.ECOMMERCE_DAG, options = {}) {
  try {
    const raw = await apiGet(`/api/graph?preset=${encodeURIComponent(preset)}`, options);
    return adaptGraphResponse(raw, { isLive: true, isFallback: false });
  } catch (err) {
    if (!isNetworkFailure(err)) {
      throw err;
    }
    console.warn(`[impactService] Live backend unreachable (${err.message}). Using fallback demonstration topology.`);
    const fallbackData = getDataset(preset);
    return adaptGraphResponse(fallbackData, {
      isLive: false,
      isFallback: true,
      fallbackReason: err.message || 'Connection refused or offline',
    });
  }
}

/**
 * Fetches all persisted projects from PostgreSQL.
 *
 * @param {object} options
 * @returns {Promise<Array<object>>} list of persisted projects
 */
export async function fetchProjects(options = {}) {
  try {
    const data = await apiGet('/api/projects', options);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    if (isNetworkFailure(err)) {
      return [];
    }
    throw err;
  }
}

/**
 * Normalizes persisted project services and dependencies into the graph structure consumed by the UI.
 *
 * @param {object} project
 * @param {Array<object>} rawServices
 * @param {Array<object>} rawDependencies
 * @returns {object} adapted graph data
 */
export function adaptPersistedGraph(project, rawServices = [], rawDependencies = []) {
  const services = (rawServices || []).map((s) => ({
    id: s.name || String(s.id),
    numericId: s.id,
    label: s.name || `Service #${s.id}`,
    type: s.type || 'Core Domain',
    tier: s.tier || 'Domain Tier',
    icon: s.icon || 'database',
    description: s.description || '',
    apiCount: s.apiCount || 0,
  }));

  const dependencies = (rawDependencies || []).map((d) => {
    const source = d.sourceServiceName || d.source;
    const target = d.targetServiceName || d.target;
    return {
      source,
      target,
      dependentId: source,
      dependencyId: target,
      type: d.dependencyType || 'REST',
      description: d.description || '',
    };
  });

  return {
    projectId: project?.id,
    preset: null,
    name: project?.name || `Project #${project?.id}`,
    description: project?.description || '',
    serviceCount: services.length,
    edgeCount: dependencies.length,
    hasCycle: false,
    services,
    dependencies,
    cycles: [],
    cycleDetails: [],
    isLive: true,
    isPersisted: true,
    isMock: false,
    isFallback: false,
    sourceNote: `Live PostgreSQL Project #${project?.id}`,
  };
}

/**
 * Fetches a persisted project's metadata, services, and dependencies and returns an adapted graph.
 *
 * @param {number|string} projectId
 * @param {object} options
 * @returns {Promise<object>} adapted project graph
 */
export async function fetchProjectGraph(projectId, options = {}) {
  if (!projectId) {
    throw new Error('Project ID is required to fetch project graph');
  }

  const [project, services, dependencies] = await Promise.all([
    apiGet(`/api/projects/${projectId}`, options),
    apiGet(`/api/projects/${projectId}/services`, options),
    apiGet(`/api/projects/${projectId}/dependencies`, options),
  ]);

  return adaptPersistedGraph(project, services, dependencies);
}

/**
 * Computes downstream change impact blast radius for a service.
 * Supports both demo presets (when projectId is null) and persisted projects (when projectId is provided).
 *
 * @param {string} changedServiceId
 * @param {string} preset
 * @param {object} options including optional projectId
 * @returns {Promise<object|null>} adapted impact analysis result
 */
export async function analyzeChangeImpact(
  changedServiceId,
  preset = DATASET_PRESETS.ECOMMERCE_DAG,
  { projectId = null, ...options } = {}
) {
  if (!changedServiceId || !changedServiceId.trim()) {
    return null;
  }
  const trimmed = changedServiceId.trim();

  // 1. Persisted project mode: explicit backend query against PostgreSQL
  if (projectId != null) {
    const raw = await apiGet(
      `/api/impact/${encodeURIComponent(trimmed)}?projectId=${encodeURIComponent(projectId)}`,
      options
    );
    return adaptImpactResponse(raw, { isLive: true, isFallback: false });
  }

  // 2. Demo mode: with offline demo fallback
  try {
    const raw = await apiGet(
      `/api/impact/${encodeURIComponent(trimmed)}?preset=${encodeURIComponent(preset)}`,
      options
    );
    return adaptImpactResponse(raw, { isLive: true, isFallback: false });
  } catch (err) {
    if (!isNetworkFailure(err)) {
      throw err;
    }
    console.warn(`[impactService] Live backend unreachable (${err.message}). Using fallback demonstration impact.`);
    const fallbackResult = getMockImpactResult(trimmed, preset);
    return adaptImpactResponse(fallbackResult, {
      isLive: false,
      isFallback: true,
      fallbackReason: err.message || 'Connection refused or offline',
    });
  }
}

/**
 * Fetches cycle detection findings for a given preset.
 *
 * @param {string} preset
 * @param {object} options
 * @returns {Promise<object>} cycle detection result
 */
export async function fetchCycleDetection(preset = DATASET_PRESETS.ECOMMERCE_DAG, options = {}) {
  try {
    const data = await apiGet(`/api/cycles?preset=${encodeURIComponent(preset)}`, options);
    return {
      hasCycle: Boolean(data.hasCycle),
      cycleCount: data.cycleCount ?? (data.cycles || []).length,
      cycles: data.cycles || [],
      isLive: true,
    };
  } catch (err) {
    if (!isNetworkFailure(err)) {
      throw err;
    }
    console.warn(`[impactService] Live backend unreachable (${err.message}). Using fallback cycle results.`);
    const fallbackData = getDataset(preset);
    return {
      hasCycle: Boolean(fallbackData.hasCycle),
      cycleCount: (fallbackData.cycleDetails || []).length,
      cycles: fallbackData.cycleDetails || [],
      isLive: false,
      isFallback: true,
    };
  }
}
