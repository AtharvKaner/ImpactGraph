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
 * Keys used for session persistence in sessionStorage.
 */
export const SESSION_TOKEN_KEY = 'impactgraph_token';
export const SESSION_USER_KEY = 'impactgraph_user';

/**
 * Accesses sessionStorage in a browser-safe and test-safe way.
 */
function getSessionStorage() {
  if (typeof window !== 'undefined' && window.sessionStorage) {
    return window.sessionStorage;
  }
  if (typeof globalThis !== 'undefined' && globalThis.sessionStorage) {
    return globalThis.sessionStorage;
  }
  return null;
}

/**
 * Retrieves the stored JWT from sessionStorage.
 * @returns {string|null}
 */
export function getStoredToken() {
  const storage = getSessionStorage();
  if (!storage) return null;
  return storage.getItem(SESSION_TOKEN_KEY);
}

/**
 * Retrieves the stored user metadata from sessionStorage.
 * @returns {object|null}
 */
export function getStoredUser() {
  const storage = getSessionStorage();
  if (!storage) return null;
  try {
    const raw = storage.getItem(SESSION_USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/**
 * Saves the authenticated session (JWT and user data) into sessionStorage.
 * @param {object} authData AuthResponse from backend
 */
export function setSession(authData) {
  const storage = getSessionStorage();
  if (!storage || !authData || !authData.token) return;
  storage.setItem(SESSION_TOKEN_KEY, authData.token);
  const user = {
    userId: authData.userId,
    email: authData.email,
    displayName: authData.displayName,
    role: authData.role,
  };
  storage.setItem(SESSION_USER_KEY, JSON.stringify(user));
}

/**
 * Clears the authenticated session from sessionStorage.
 */
export function clearSession() {
  const storage = getSessionStorage();
  if (!storage) return;
  storage.removeItem(SESSION_TOKEN_KEY);
  storage.removeItem(SESSION_USER_KEY);
}

/**
 * Checks whether an active authentication token exists.
 * @returns {boolean}
 */
export function isAuthenticated() {
  return Boolean(getStoredToken());
}

/**
 * Checks whether an endpoint path is public in SecurityConfig.
 * Public endpoints: /api/health, /api/auth/login, /api/auth/register, /api/transparency/public.
 * @param {string} path
 * @returns {boolean}
 */
export function isPublicEndpoint(path) {
  if (!path) return false;
  const clean = path.split('?')[0].replace(/\/+$/, '');
  return (
    clean === '/api/auth/login' ||
    clean === '/api/auth/register' ||
    clean === '/api/health' ||
    clean === '/api/transparency/public' ||
    clean === '/api/github/webhook' ||
    clean === '/api/github/status'
  );
}

/**
 * Helper to perform HTTP requests with timeout, signal cancellation,
 * automatic JWT Bearer token attachment, and clean error parsing.
 *
 * @param {string} path API path (e.g. '/api/projects')
 * @param {object} options request options including method, body, signal, timeout (ms), auth override
 * @returns {Promise<any>} parsed JSON response
 */
export async function apiFetch(path, {
  method = 'GET',
  body,
  timeout = 5000,
  signal: externalSignal,
  headers = {},
  auth,
} = {}) {
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

  const requestHeaders = {
    Accept: 'application/json',
    ...headers,
  };

  if (body !== undefined && !requestHeaders['Content-Type']) {
    requestHeaders['Content-Type'] = 'application/json';
  }

  // Token attachment logic:
  // If auth is explicitly false, do not attach token.
  // Otherwise, attach token for non-public endpoints if token exists in session.
  const isPublic = isPublicEndpoint(cleanPath);
  const shouldAttachToken = auth === true || (auth !== false && !isPublic);
  const token = getStoredToken();
  if (shouldAttachToken && token && !requestHeaders['Authorization']) {
    requestHeaders['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      method,
      headers: requestHeaders,
      body: body !== undefined
        ? (typeof body === 'string' ? body : JSON.stringify(body))
        : undefined,
      signal: controller.signal,
    });

    if (!response.ok) {
      let errorBody = null;
      try {
        errorBody = await response.json();
      } catch {
        // Non-JSON response
      }
      if (response.status === 401 && shouldAttachToken) {
        clearSession();
      }
      const message = errorBody?.message || `Backend returned HTTP ${response.status} (${response.statusText})`;
      throw new HttpError(response.status, response.statusText, message, errorBody);
    }

    if (response.status === 204) {
      return null;
    }
    const contentType = response.headers?.get?.('content-type');
    if (contentType && !contentType.includes('application/json')) {
      return await response.text();
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
 * Performs GET requests using apiFetch.
 */
export async function apiGet(path, options = {}) {
  return await apiFetch(path, { ...options, method: 'GET' });
}

/**
 * Performs POST requests using apiFetch.
 */
export async function apiPost(path, body, options = {}) {
  return await apiFetch(path, { ...options, method: 'POST', body });
}

/**
 * Authenticates user credentials via POST /api/auth/login.
 * On success, saves JWT and user metadata to sessionStorage and returns AuthResponse.
 * On failure, throws HttpError without exposing or persisting credentials.
 *
 * @param {string} email
 * @param {string} password
 * @param {object} options
 * @returns {Promise<{ token: string, email: string, displayName: string, role: string, userId: string }>}
 */
export async function login(email, password, options = {}) {
  if (!email || !email.trim()) {
    throw new Error('Email is required');
  }
  if (!password) {
    throw new Error('Password is required');
  }

  const payload = {
    email: email.trim(),
    password,
  };

  const data = await apiPost('/api/auth/login', payload, {
    ...options,
    auth: false,
  });

  if (data && data.token) {
    setSession(data);
  }

  return data;
}

/**
 * Logs out the current user by clearing the session from sessionStorage.
 */
export function logout() {
  clearSession();
}

/**
 * Registers a new user via POST /api/auth/register.
 *
 * @param {string} email
 * @param {string} password
 * @param {string} displayName
 * @param {object} options
 * @returns {Promise<{ token: string, email: string, displayName: string, role: string, userId: string }>}
 */
export async function register(email, password, displayName, options = {}) {
  if (!email || !email.trim()) {
    throw new Error('Email is required');
  }
  if (!password) {
    throw new Error('Password is required');
  }

  const payload = {
    email: email.trim(),
    password,
    displayName: displayName?.trim() || email.split('@')[0],
  };

  const data = await apiPost('/api/auth/register', payload, {
    ...options,
    auth: false,
  });

  if (data && data.token) {
    setSession(data);
  }

  return data;
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

/**
 * Fetches filtered security findings from /api/security/findings.
 */
export async function fetchSecurityFindings({ status, severity, ruleId, projectId } = {}, options = {}) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (severity) params.set('severity', severity);
  if (ruleId) params.set('ruleId', ruleId);
  if (projectId) params.set('projectId', projectId);

  const query = params.toString();
  const path = query ? `/api/security/findings?${query}` : '/api/security/findings';

  try {
    const data = await apiGet(path, options);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    console.warn(`[impactService] Live backend unreachable for findings (${err.message}). Using offline demo findings.`);
    return [
      {
        id: 'mock-finding-1',
        ruleId: 'SEC-TAINT-003',
        title: 'Project-Scoped Order Access Missing IDOR Verification',
        description: 'Order query endpoint accepts project resource ID but does not verify caller has project membership before querying database.',
        severity: 'HIGH',
        confidence: 'CONFIRMED',
        status: 'OPEN',
        baseRevision: 'develop@c44e1d9',
        targetRevision: 'feat/order-export@HEAD',
        filePath: 'backend/src/main/java/com/impactgraph/controller/ProjectController.java',
        startLine: 54,
        endLine: 62,
        affectedEndpoint: 'GET /api/projects/{projectId}/services',
        httpMethod: 'GET',
        affectedComponent: 'Order Service',
        evidencePath: 'ProjectController#getServicesForProject -> serviceService.getAllServices(projectId)',
        explanation: 'Without calling projectService.enforceProjectAccess(user, projectId), an authenticated user from Project A can read service topologies of Project B.',
        suggestedRemediation: 'Add projectService.enforceProjectAccess(user, projectId) before service execution.',
        suggestedTests: 'com.impactgraph.SecurityIntegrationTest#accessOtherProject_forbidden',
        createdAt: new Date(Date.now() - 3600 * 4000).toISOString(),
      },
      {
        id: 'mock-finding-2',
        ruleId: 'SEC-DEPS-006',
        title: 'High-Blast-Radius Shared Security Configuration Modification',
        description: 'Core Spring Security configuration was updated, affecting authorization rules across all backend API endpoints.',
        severity: 'MEDIUM',
        confidence: 'HIGH',
        status: 'IN_REVIEW',
        baseRevision: 'main@14ce8c6',
        targetRevision: 'develop@495be18',
        filePath: 'backend/src/main/java/com/impactgraph/security/SecurityConfig.java',
        startLine: 60,
        endLine: 91,
        affectedEndpoint: 'ALL /api/**',
        httpMethod: 'ALL',
        affectedComponent: 'SecurityConfig',
        evidencePath: 'SecurityConfig.java -> filterChain() adds /api/admin/** and /api/audit/** rules',
        explanation: 'Changes to filterChain affect token extraction, CORS headers, and unauthorized error responses for every client request.',
        suggestedRemediation: 'Verify all 35 security tests in SecurityIntegrationTest pass without error.',
        suggestedTests: 'com.impactgraph.SecurityIntegrationTest',
        createdAt: new Date(Date.now() - 3600 * 12000).toISOString(),
      },
      {
        id: 'mock-finding-3',
        ruleId: 'SEC-AUTHZ-002',
        title: 'Administrative Project Deletion Missing Role Enforcement',
        description: 'DELETE /api/projects/{projectId} allowed any authenticated user to delete a project without ADMIN role check.',
        severity: 'CRITICAL',
        confidence: 'CONFIRMED',
        status: 'VERIFIED',
        baseRevision: 'develop@initial',
        targetRevision: 'develop@495be18',
        filePath: 'backend/src/main/java/com/impactgraph/controller/ProjectController.java',
        startLine: 75,
        endLine: 86,
        affectedEndpoint: 'DELETE /api/projects/{projectId}',
        httpMethod: 'DELETE',
        affectedComponent: 'ProjectController',
        evidencePath: 'ProjectController#deleteProject -> verified ADMIN check added',
        explanation: 'The developer role check was properly added and verified by automated regression tests.',
        suggestedRemediation: 'Enforce ADMIN role check on mutating routes.',
        suggestedTests: 'com.impactgraph.SecurityIntegrationTest#deleteProject_developer_accessDenied',
        verificationNotes: 'Verified resolved: RBAC enforcement confirmed via SecurityIntegrationTest with 0 errors.',
        createdAt: new Date(Date.now() - 3600 * 24000).toISOString(),
        verifiedAt: new Date(Date.now() - 3600 * 2000).toISOString(),
      }
    ];
  }
}

/**
 * Fetches a single finding by ID.
 */
export async function fetchFindingById(id, options = {}) {
  return await apiGet(`/api/security/findings/${id}`, options);
}

/**
 * Re-runs deterministic verification checks on a finding.
 */
export async function verifyFinding(id, options = {}) {
  try {
    return await apiPost(`/api/security/findings/${id}/verify`, {}, options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      findingId: id,
      previousStatus: 'OPEN',
      newStatus: 'VERIFIED',
      verifiedResolved: true,
      verificationMessage: 'Offline Simulation: Verified deterministic check passes on ProjectController role enforcement.',
      executedCheck: 'Offline AST validation fixture',
    };
  }
}

/**
 * Fetches security-annotated API Inventory from /api/security/api-inventory.
 */
export async function fetchApiInventory(options = {}) {
  try {
    const data = await apiGet('/api/security/api-inventory', options);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return [
      { method: 'GET', path: '/api/health', controllerName: 'HealthController', handlerMethod: 'health', authRequirement: 'PUBLIC', requiredRole: null, hasPreAuthorize: false, isPermitAll: true, securitySource: 'SecurityConfig.permitAll()', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
      { method: 'POST', path: '/api/auth/login', controllerName: 'AuthController', handlerMethod: 'login', authRequirement: 'PUBLIC', requiredRole: null, hasPreAuthorize: false, isPermitAll: true, securitySource: 'SecurityConfig.permitAll()', sensitiveFields: ['password'], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
      { method: 'POST', path: '/api/auth/register', controllerName: 'AuthController', handlerMethod: 'register', authRequirement: 'PUBLIC', requiredRole: null, hasPreAuthorize: false, isPermitAll: true, securitySource: 'SecurityConfig.permitAll()', sensitiveFields: ['password'], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
      { method: 'GET', path: '/api/projects', controllerName: 'ProjectController', handlerMethod: 'getAllProjects', authRequirement: 'AUTHENTICATED', requiredRole: null, hasPreAuthorize: false, isPermitAll: false, securitySource: 'SecurityConfig.authenticated()', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
      { method: 'POST', path: '/api/projects', controllerName: 'ProjectController', handlerMethod: 'createProject', authRequirement: 'ROLE_BASED', requiredRole: 'ROLE_ADMIN', hasPreAuthorize: false, isPermitAll: false, securitySource: 'Controller Role Check (ADMIN)', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'MEDIUM' },
      { method: 'DELETE', path: '/api/projects/{projectId}', controllerName: 'ProjectController', handlerMethod: 'deleteProject', authRequirement: 'ROLE_BASED', requiredRole: 'ROLE_ADMIN', hasPreAuthorize: false, isPermitAll: false, securitySource: 'Controller Role Check (ADMIN)', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'HIGH' },
      { method: 'GET', path: '/api/audit/events', controllerName: 'AuditController', handlerMethod: 'getEvents', authRequirement: 'ROLE_BASED', requiredRole: 'ROLE_ADMIN', hasPreAuthorize: true, isPermitAll: false, securitySource: '@PreAuthorize("hasRole(\'ADMIN\')")', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
      { method: 'POST', path: '/api/admin/memberships', controllerName: 'AdminController', handlerMethod: 'grantMembership', authRequirement: 'ROLE_BASED', requiredRole: 'ROLE_ADMIN', hasPreAuthorize: true, isPermitAll: false, securitySource: '@PreAuthorize("hasRole(\'ADMIN\')")', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'MEDIUM' },
      { method: 'GET', path: '/api/transparency/public', controllerName: 'TransparencyController', handlerMethod: 'getPublicTransparency', authRequirement: 'PUBLIC', requiredRole: null, hasPreAuthorize: false, isPermitAll: true, securitySource: 'SecurityConfig.permitAll()', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
      { method: 'GET', path: '/api/transparency/my-activity', controllerName: 'TransparencyController', handlerMethod: 'getMyActivity', authRequirement: 'AUTHENTICATED', requiredRole: null, hasPreAuthorize: false, isPermitAll: false, securitySource: 'SecurityConfig.authenticated()', sensitiveFields: [], driftStatus: 'UNCHANGED', riskLevel: 'SAFE' },
    ];
  }
}

/**
 * Fetches analysis run history.
 */
export async function fetchAnalysisHistory(projectId, options = {}) {
  const url = projectId ? `/api/security/history?projectId=${encodeURIComponent(projectId)}` : '/api/security/history';
  try {
    const data = await apiGet(url, options);
    return Array.isArray(data) ? data : [];
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return [
      {
        id: 'run-1',
        runType: 'MANUAL',
        baseRevision: 'develop@c44e1d9',
        targetRevision: 'feat/dashboard-redesign@HEAD',
        timestamp: new Date().toISOString(),
        totalFindings: 3,
        criticalCount: 1,
        highCount: 1,
        mediumCount: 1,
        lowCount: 0,
        status: 'COMPLETED',
        summary: 'Baseline change-impact and security regression scan completed across 10 API endpoints.',
      }
    ];
  }
}

/**
 * Executes security change-impact analysis against a target revision.
 */
export async function triggerSecurityAnalysis({ projectId, baseRevision, targetRevision } = {}, options = {}) {
  const params = new URLSearchParams();
  if (projectId) params.set('projectId', projectId);
  if (baseRevision) params.set('baseRevision', baseRevision);
  if (targetRevision) params.set('targetRevision', targetRevision);

  const url = `/api/security/analyze?${params.toString()}`;
  return await apiPost(url, {}, options);
}

/**
 * Fetches AI-assisted explanation and remediation snippet for a finding.
 */
export async function fetchAiExplanation(findingId, options = {}) {
  try {
    return await apiGet(`/api/security/findings/${findingId}/ai-explanation`, options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      findingId,
      ruleId: 'SEC-TAINT-003',
      threatSummary: 'Insecure Direct Object Reference (IDOR): A user can access or manipulate resources of another organization or project by guessing or tampering with the URL identifier.',
      impactRationale: 'The controller extracts the resource ID directly from the path variable without checking that the caller belongs to the tenant project.',
      suggestedRemediation: 'Add projectService.enforceProjectAccess(user, projectId) before executing domain logic.',
      remediationCodeSnippet: 'public void enforceProjectAccess(JwtAuthenticatedUser user, Long projectId) {\n    if (!canAccessProject(user, projectId)) throw new SecurityException("Access denied");\n}',
      suggestedRegressionTests: ['com.impactgraph.SecurityIntegrationTest#accessOtherProject_forbidden'],
      limitationsAndUncertainty: 'Offline analysis knowledge engine. Static graph reachability does not guarantee exploitability without runtime validation.',
      isAiGenerated: false,
      modelProvider: 'Deterministic Security Knowledge Engine',
    };
  }
}

/**
 * Fetches paginated audit events from /api/audit/events (requires ADMIN).
 */
export async function fetchAuditEvents({ page = 0, size = 20, projectId, eventType } = {}, options = {}) {
  let url = `/api/audit/events?page=${page}&size=${size}`;
  if (projectId) url = `/api/audit/events/by-project/${projectId}?page=${page}&size=${size}`;
  else if (eventType) url = `/api/audit/events/by-type/${eventType}?page=${page}&size=${size}`;

  try {
    return await apiGet(url, options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      content: [
        { id: '1', eventType: 'LOGIN_SUCCESS', actorEmail: 'admin@impactgraph.io', outcome: 'SUCCESS', description: 'User login authenticated', clientIp: '127.0.0.1', timestamp: new Date(Date.now() - 600000).toISOString() },
        { id: '2', eventType: 'PROJECT_CREATED', actorEmail: 'admin@impactgraph.io', outcome: 'SUCCESS', description: 'Created project: E-Commerce Production', clientIp: '127.0.0.1', timestamp: new Date(Date.now() - 1800000).toISOString() },
        { id: '3', eventType: 'MEMBERSHIP_GRANTED', actorEmail: 'admin@impactgraph.io', outcome: 'SUCCESS', description: 'Granted DEVELOPER to dev@impactgraph.io', clientIp: '127.0.0.1', timestamp: new Date(Date.now() - 3600000).toISOString() },
        { id: '4', eventType: 'ACCESS_DENIED', actorEmail: 'dev@impactgraph.io', outcome: 'FAILURE', description: 'Cross-project access denied', clientIp: '192.168.1.15', timestamp: new Date(Date.now() - 7200000).toISOString() },
      ],
      totalElements: 4,
      totalPages: 1,
      number: 0,
    };
  }
}

/**
 * Fetches user activity from /api/transparency/my-activity (available to all authenticated users).
 */
export async function fetchMyActivity({ page = 0, size = 10 } = {}, options = {}) {
  try {
    return await apiGet(`/api/transparency/my-activity?page=${page}&size=${size}`, options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      userId: 'offline-user-id',
      email: 'engineer@impactgraph.io',
      role: 'DEVELOPER',
      recentActivity: [
        { id: '1', eventType: 'LOGIN_SUCCESS', outcome: 'SUCCESS', description: 'Authenticated successfully', timestamp: new Date(Date.now() - 900000).toISOString() },
        { id: '2', eventType: 'PROJECT_UPDATED', outcome: 'SUCCESS', description: 'Inspected dependency graph topology', timestamp: new Date(Date.now() - 3600000).toISOString() },
      ],
      totalEvents: 2,
      page: 0,
      totalPages: 1,
    };
  }
}

/**
 * Fetches public transparency disclosure from /api/transparency/public.
 */
export async function fetchPublicTransparency(options = {}) {
  try {
    return await apiGet('/api/transparency/public', options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      dataCollected: [
        { category: 'Account Identity', fields: ['Email address', 'Display name', 'Hashed password (BCrypt)', 'Role (ADMIN or DEVELOPER)'], purpose: 'Authentication and authorization' },
        { category: 'Project Metadata', fields: ['Project name', 'Description', 'Creation/update timestamps'], purpose: 'Organizing dependency analysis workspaces' },
        { category: 'Project Membership', fields: ['User-to-project associations', 'Who granted membership'], purpose: 'Controlling project-level access' },
        { category: 'Security Events (Audit Log)', fields: ['Event type', 'Timestamp', 'Actor email', 'Project/resource ID', 'Outcome', 'Client IP'], purpose: 'Security monitoring and compliance' },
        { category: 'Services, APIs, and Dependencies', fields: ['Defined by core module and source AST parser'], purpose: 'Software dependency and change-impact analysis' }
      ],
      dataStorage: {
        database: 'PostgreSQL',
        location: 'Configured by deployment (DB_URL)',
        encryption: 'Not enforced by the application layer; depends on PostgreSQL and storage infra.',
        backups: 'Managed by infrastructure operations.'
      },
      graphAnalysis: {
        description: 'ImpactGraph analyzes software dependencies to determine change impact across services and APIs.',
        dataUsed: 'Project services, APIs, and dependency relationships',
        status: 'Active: BFS/DFS reverse reachability blast-radius engine with Tarjan cycle detector.'
      },
      accessControl: {
        ADMIN: 'Can manage all projects, grant/revoke memberships, view complete audit logs, and administer users.',
        DEVELOPER: 'Can access only projects they have been granted membership to. Cannot view full audit logs or manage other users.'
      },
      dataRetention: {
        auditEvents: 'Retained indefinitely in the database for auditability and compliance.',
        userAccounts: 'Persist until manually removed by an administrator.',
        limitation: 'Automatic scheduled data deletion policies are not yet implemented.'
      }
    };
  }
}

/**
 * Fetches GitHub webhook integration status.
 */
export async function fetchGitHubStatus(options = {}) {
  try {
    return await apiGet('/api/github/status', options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      configured: false,
      monitoredEvents: 'pull_request',
      endpoint: '/api/github/webhook',
      note: 'Offline demonstration mode: configure GITHUB_WEBHOOK_SECRET to enable live HMAC verification.',
    };
  }
}

/**
 * Fetches the seeded deterministic demo project from /api/projects/demo.
 */
export async function fetchDemoProject(options = {}) {
  try {
    return await apiGet('/api/projects/demo', options);
  } catch (err) {
    if (!isNetworkFailure(err)) throw err;
    return {
      id: 1,
      name: '[Demo] E-Commerce Platform',
      description: 'Deterministic 8-service multi-tier e-commerce platform demo architecture with security findings.',
    };
  }
}

/**
 * Imports a real GitHub repository or local directory and triggers architectural analysis.
 *
 * @param {object} payload { sourceType, repositoryUrl, localPath, branch, personalAccessToken, projectName }
 * @param {object} options
 * @returns {Promise<object>} RepositoryAnalysisResponseDto
 */
export async function importRepository(payload, options = {}) {
  return await apiPost('/api/repositories/import', payload, options);
}

/**
 * Compares two Git revisions on a project and calculates downstream change impact.
 *
 * @param {object} payload { projectId, baseRevision, targetRevision, repositoryUrl, localPath, personalAccessToken }
 * @param {object} options
 * @returns {Promise<object>} RevisionComparisonResponseDto
 */
export async function compareRevisions(payload, options = {}) {
  if (!payload || !payload.projectId) {
    throw new Error('Project ID is required to compare revisions');
  }
  return await apiPost('/api/repositories/compare-revisions', payload, options);
}

/**
 * Discovers and inspects the local workspace repository.
 *
 * @param {object} options
 * @returns {Promise<object>}
 */
export async function fetchLocalWorkspaceInfo(options = {}) {
  return await apiGet('/api/repositories/local-workspace', options);
}

/**
 * Retrieves recent revisions / commit logs for the workspace.
 *
 * @param {number|object} limit
 * @param {object} options
 * @returns {Promise<Array<object>>}
 */
export async function fetchWorkspaceRevisions(limit = 10, options = {}) {
  const queryLimit = typeof limit === 'number' ? `?limit=${limit}` : '';
  const opts = typeof limit === 'object' ? limit : options;
  return await apiGet(`/api/repositories/revisions${queryLimit}`, opts);
}

export const analyzeSecurityDiff = triggerSecurityAnalysis;
export const fetchHealth = checkBackendHealth;
