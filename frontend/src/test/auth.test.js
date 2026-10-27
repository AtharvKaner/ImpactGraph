import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import {
  login,
  logout,
  getStoredToken,
  getStoredUser,
  setSession,
  clearSession,
  isAuthenticated,
  isPublicEndpoint,
  fetchProjects,
  checkBackendHealth,
  HttpError,
  SESSION_TOKEN_KEY,
  SESSION_USER_KEY,
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

describe('ImpactGraph Authentication Integration', () => {
  let originalFetch;
  let mockStorage;

  beforeEach(() => {
    mockStorage = new MockSessionStorage();
    globalThis.sessionStorage = mockStorage;
    globalThis.window = { sessionStorage: mockStorage };
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    delete globalThis.sessionStorage;
    delete globalThis.window;
  });

  describe('Login Flow', () => {
    it('successfully logs in, saves JWT and user metadata to sessionStorage, and returns AuthResponse', async () => {
      let capturedUrl = null;
      let capturedOptions = null;

      globalThis.fetch = async (url, options) => {
        capturedUrl = url;
        capturedOptions = options;
        return {
          ok: true,
          status: 200,
          json: async () => ({
            token: 'mock-jwt-token-xyz-123',
            email: 'admin@impactgraph.io',
            displayName: 'System Admin',
            role: 'ADMIN',
            userId: 'a0000000-0000-0000-0000-000000000001',
          }),
        };
      };

      const result = await login('admin@impactgraph.io', 'ValidAdminPassword123');

      // Verify request details
      assert.ok(capturedUrl.endsWith('/api/auth/login'), `URL should target /api/auth/login, got ${capturedUrl}`);
      assert.equal(capturedOptions.method, 'POST');
      assert.equal(capturedOptions.headers['Authorization'], undefined, 'Login request must not send Authorization header');
      assert.equal(capturedOptions.headers['Content-Type'], 'application/json');

      const parsedBody = JSON.parse(capturedOptions.body);
      assert.equal(parsedBody.email, 'admin@impactgraph.io');
      assert.equal(parsedBody.password, 'ValidAdminPassword123');

      // Verify response returned
      assert.equal(result.token, 'mock-jwt-token-xyz-123');
      assert.equal(result.email, 'admin@impactgraph.io');
      assert.equal(result.role, 'ADMIN');

      // Verify session persistence in sessionStorage
      assert.equal(getStoredToken(), 'mock-jwt-token-xyz-123');
      assert.deepEqual(getStoredUser(), {
        userId: 'a0000000-0000-0000-0000-000000000001',
        email: 'admin@impactgraph.io',
        displayName: 'System Admin',
        role: 'ADMIN',
      });
      assert.equal(isAuthenticated(), true);
    });

    it('handles login failure (401 Bad Credentials) without saving tokens or leaking credentials', async () => {
      globalThis.fetch = async () => {
        return {
          ok: false,
          status: 401,
          statusText: 'Unauthorized',
          json: async () => ({
            status: 401,
            message: 'Invalid email or password',
          }),
        };
      };

      await assert.rejects(
        async () => {
          await login('wrong@example.com', 'BadPassword');
        },
        (err) => {
          assert.ok(err instanceof HttpError);
          assert.equal(err.status, 401);
          assert.equal(err.message, 'Invalid email or password');
          return true;
        }
      );

      // Verify no session was stored
      assert.equal(getStoredToken(), null);
      assert.equal(getStoredUser(), null);
      assert.equal(isAuthenticated(), false);
    });

    it('validates required fields before sending request', async () => {
      await assert.rejects(async () => {
        await login('', 'password123');
      }, /Email is required/);

      await assert.rejects(async () => {
        await login('user@example.com', '');
      }, /Password is required/);
    });
  });

  describe('Authenticated API Requests & Header Attachment', () => {
    it('automatically attaches Authorization: Bearer <token> to protected endpoints when logged in', async () => {
      // Setup authenticated session
      setSession({
        token: 'auth-jwt-bearer-token',
        email: 'engineer@impactgraph.io',
        displayName: 'Alice Engineer',
        role: 'DEVELOPER',
        userId: 'user-uuid-2',
      });

      let capturedHeaders = null;
      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          ok: true,
          status: 200,
          json: async () => [{ id: 1, name: 'ImpactGraph Core' }],
        };
      };

      const projects = await fetchProjects();
      assert.equal(projects.length, 1);
      assert.equal(capturedHeaders['Authorization'], 'Bearer auth-jwt-bearer-token');
    });

    it('does NOT attach Authorization header when no session exists', async () => {
      clearSession();

      let capturedHeaders = null;
      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          ok: true,
          status: 200,
          json: async () => [],
        };
      };

      await fetchProjects();
      assert.equal(capturedHeaders['Authorization'], undefined);
    });

    it('does NOT attach Authorization header to public endpoints even when logged in', async () => {
      setSession({
        token: 'auth-jwt-bearer-token',
        email: 'engineer@impactgraph.io',
        displayName: 'Alice Engineer',
        role: 'DEVELOPER',
        userId: 'user-uuid-2',
      });

      let capturedHeaders = null;
      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          ok: true,
          status: 200,
          json: async () => ({ status: 'UP', service: 'impactgraph-backend' }),
        };
      };

      await checkBackendHealth();
      assert.equal(capturedHeaders['Authorization'], undefined, 'Public /api/health endpoint should not receive Authorization header');
    });

    it('identifies public endpoints correctly according to SecurityConfig', () => {
      assert.equal(isPublicEndpoint('/api/auth/login'), true);
      assert.equal(isPublicEndpoint('/api/auth/register'), true);
      assert.equal(isPublicEndpoint('/api/health'), true);
      assert.equal(isPublicEndpoint('/api/transparency/public'), true);

      assert.equal(isPublicEndpoint('/api/projects'), false);
      assert.equal(isPublicEndpoint('/api/projects/1'), false);
      assert.equal(isPublicEndpoint('/api/impact/Order%20Service'), false);
      assert.equal(isPublicEndpoint('/api/admin/users'), false);
    });
  });

  describe('Logout & Session Clearing', () => {
    it('logout clears session storage and subsequent requests omit Authorization header', async () => {
      setSession({
        token: 'token-to-be-cleared',
        email: 'user@example.com',
        displayName: 'User',
        role: 'DEVELOPER',
        userId: 'uuid-user',
      });

      assert.equal(isAuthenticated(), true);

      // Perform logout
      logout();

      assert.equal(getStoredToken(), null);
      assert.equal(getStoredUser(), null);
      assert.equal(isAuthenticated(), false);

      // Verify subsequent request does not send token
      let capturedHeaders = null;
      globalThis.fetch = async (url, options) => {
        capturedHeaders = options.headers;
        return {
          ok: true,
          status: 200,
          json: async () => [],
        };
      };

      await fetchProjects();
      assert.equal(capturedHeaders['Authorization'], undefined);
    });

    it('clears expired session when protected endpoint responds with 401 Unauthorized', async () => {
      setSession({
        token: 'expired-token',
        email: 'user@example.com',
        displayName: 'User',
        role: 'DEVELOPER',
        userId: 'uuid-user',
      });

      assert.equal(isAuthenticated(), true);

      globalThis.fetch = async () => {
        return {
          ok: false,
          status: 401,
          statusText: 'Unauthorized',
          json: async () => ({ status: 401, message: 'Authentication required' }),
        };
      };

      await assert.rejects(async () => {
        await fetchProjects();
      }, HttpError);

      // Token must have been wiped due to 401
      assert.equal(getStoredToken(), null);
      assert.equal(isAuthenticated(), false);
    });
  });

  describe('Session Restoration on Page Refresh', () => {
    it('restores stored token and user metadata from sessionStorage', () => {
      mockStorage.setItem(SESSION_TOKEN_KEY, 'restored-token-999');
      mockStorage.setItem(
        SESSION_USER_KEY,
        JSON.stringify({
          userId: 'restored-uuid',
          email: 'admin@impactgraph.io',
          displayName: 'Admin Restored',
          role: 'ADMIN',
        })
      );

      assert.equal(getStoredToken(), 'restored-token-999');
      const user = getStoredUser();
      assert.equal(user.email, 'admin@impactgraph.io');
      assert.equal(user.displayName, 'Admin Restored');
      assert.equal(user.role, 'ADMIN');
      assert.equal(isAuthenticated(), true);
    });
  });
});
