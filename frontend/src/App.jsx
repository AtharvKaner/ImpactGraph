import React, { useState, useEffect, useCallback } from 'react';

// Fallback public transparency data in case backend is offline during static preview
const DEFAULT_TRANSPARENCY = {
  dataCollected: [
    {
      category: "Account Identity",
      fields: ["Email address", "Display name", "Hashed password (BCrypt)", "Role (ADMIN or DEVELOPER)"],
      purpose: "Authentication and authorization"
    },
    {
      category: "Project Metadata",
      fields: ["Project name", "Description", "Creation/update timestamps"],
      purpose: "Organizing dependency analysis workspaces"
    },
    {
      category: "Project Membership",
      fields: ["User-to-project associations", "Who granted membership"],
      purpose: "Controlling project-level access"
    },
    {
      category: "Security Events (Audit Log)",
      fields: ["Event type", "Timestamp", "Actor email", "Project/resource ID", "Outcome", "Client IP"],
      purpose: "Security monitoring and compliance"
    },
    {
      category: "Services, APIs, and Dependencies",
      fields: ["Defined by core module (under development)"],
      purpose: "Software dependency and change-impact analysis"
    }
  ],
  dataStorage: {
    database: "PostgreSQL",
    location: "Configured by deployment (see application.properties DB_URL)",
    encryption: "Not enforced by the application layer. Depends on PostgreSQL and infrastructure configuration.",
    backups: "Not managed by the application. Depends on infrastructure setup."
  },
  graphAnalysis: {
    description: "ImpactGraph analyzes software dependencies to determine change impact across services and APIs.",
    dataUsed: "Project services, APIs, and dependency relationships",
    status: "Core graph analysis module is under development by the backend team."
  },
  accessControl: {
    ADMIN: "Can manage all projects, grant/revoke memberships, view audit logs, and administer users.",
    DEVELOPER: "Can access only projects they have been granted membership to. Cannot view audit logs or manage other users."
  },
  dataRetention: {
    auditEvents: "Retained indefinitely in the database. No automatic deletion is currently implemented.",
    userAccounts: "Persist until manually removed by an administrator.",
    limitation: "Automatic data retention policies and scheduled deletion are not yet implemented."
  }
};

export default function App() {
  const [activeTab, setActiveTab] = useState('transparency');
  const [health, setHealth] = useState({ status: 'checking', latency: null });
  const [transparency, setTransparency] = useState(DEFAULT_TRANSPARENCY);
  
  // Auth state
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const saved = localStorage.getItem('impactgraph_user');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });

  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authMode, setAuthMode] = useState('login'); // 'login' | 'register'
  const [authForm, setAuthForm] = useState({ email: '', password: '', displayName: '' });
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);

  // My Activity state
  const [myActivity, setMyActivity] = useState(null);
  const [activityLoading, setActivityLoading] = useState(false);

  // Admin audit state
  const [adminAuditEvents, setAdminAuditEvents] = useState([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const [auditFilterType, setAuditFilterType] = useState('ALL');

  // Sandbox state
  const [sandboxResponse, setSandboxResponse] = useState(null);
  const [sandboxLoading, setSandboxLoading] = useState(false);

  const checkHealth = useCallback(async () => {
    const start = performance.now();
    try {
      const res = await fetch('/api/health');
      const latency = Math.round(performance.now() - start);
      if (res.ok) {
        setHealth({ status: 'UP', latency });
      } else {
        setHealth({ status: 'DOWN', latency });
      }
    } catch {
      setHealth({ status: 'OFFLINE', latency: null });
    }
  }, []);

  const fetchPublicTransparency = useCallback(async () => {
    try {
      const res = await fetch('/api/transparency/public');
      if (res.ok) {
        const data = await res.json();
        setTransparency(data);
      }
    } catch {
      // Keep DEFAULT_TRANSPARENCY
    }
  }, []);

  const fetchMyActivity = useCallback(async () => {
    if (!currentUser?.token) return;
    setActivityLoading(true);
    try {
      const res = await fetch('/api/transparency/my-activity', {
        headers: { Authorization: `Bearer ${currentUser.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setMyActivity(data);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setActivityLoading(false);
    }
  }, [currentUser]);

  const fetchAdminAudit = useCallback(async () => {
    if (!currentUser?.token || currentUser.role !== 'ADMIN') return;
    setAuditLoading(true);
    try {
      const url = auditFilterType === 'ALL'
        ? '/api/audit/events?size=50'
        : `/api/audit/events/by-type/${auditFilterType}?size=50`;
      
      const res = await fetch(url, {
        headers: { Authorization: `Bearer ${currentUser.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setAdminAuditEvents(data.content || []);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setAuditLoading(false);
    }
  }, [currentUser, auditFilterType]);

  // Health check on mount & periodically
  useEffect(() => {
    checkHealth();
    fetchPublicTransparency();
    const interval = setInterval(checkHealth, 15000);
    return () => clearInterval(interval);
  }, [checkHealth, fetchPublicTransparency]);

  // When user changes or activeTab is 'my-activity', fetch user activity
  useEffect(() => {
    if (activeTab === 'my-activity' && currentUser) {
      fetchMyActivity();
    } else if (activeTab === 'admin' && currentUser?.role === 'ADMIN') {
      fetchAdminAudit();
    }
  }, [activeTab, currentUser, auditFilterType, fetchMyActivity, fetchAdminAudit]);

  const handleAuthSubmit = async (e) => {
    e.preventDefault();
    setAuthError('');
    setAuthLoading(true);

    const endpoint = authMode === 'login' ? '/api/auth/login' : '/api/auth/register';
    const payload = authMode === 'login'
      ? { email: authForm.email, password: authForm.password }
      : { email: authForm.email, password: authForm.password, displayName: authForm.displayName };

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();
      if (!res.ok) {
        setAuthError(data.message || 'Authentication failed. Please verify credentials.');
      } else {
        localStorage.setItem('impactgraph_user', JSON.stringify(data));
        setCurrentUser(data);
        setAuthModalOpen(false);
        setAuthForm({ email: '', password: '', displayName: '' });
        checkHealth();
      }
    } catch {
      setAuthError('Connection error: could not reach backend server.');
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('impactgraph_user');
    setCurrentUser(null);
    setMyActivity(null);
    setAdminAuditEvents([]);
    if (activeTab === 'admin' || activeTab === 'my-activity') {
      setActiveTab('transparency');
    }
  };

  const runSandboxTest = async (testName, path, method = 'GET', auth = 'none') => {
    setSandboxLoading(true);
    const headers = {};
    if (auth === 'bearer' && currentUser?.token) {
      headers['Authorization'] = `Bearer ${currentUser.token}`;
    }

    const start = performance.now();
    try {
      const res = await fetch(path, { method, headers });
      const duration = Math.round(performance.now() - start);
      let body;
      try {
        body = await res.json();
      } catch {
        body = await res.text();
      }
      setSandboxResponse({
        testName,
        path,
        method,
        status: res.status,
        statusText: res.statusText,
        duration: `${duration}ms`,
        body: typeof body === 'object' ? JSON.stringify(body, null, 2) : body
      });
    } catch (err) {
      setSandboxResponse({
        testName,
        path,
        method,
        status: 'NETWORK_ERROR',
        statusText: err.message,
        duration: '0ms',
        body: 'Failed to connect to backend server. Ensure backend is running at http://localhost:8080'
      });
    } finally {
      setSandboxLoading(false);
    }
  };

  const getEventBadgeClass = (eventType = '') => {
    if (eventType.includes('LOGIN')) return eventType.includes('FAIL') ? 'badge-event fail' : 'badge-event login';
    if (eventType.includes('PROJECT')) return 'badge-event project';
    if (eventType.includes('MEMBERSHIP')) return 'badge-event membership';
    if (eventType.includes('DENIED')) return 'badge-event fail';
    return 'badge-event security';
  };

  return (
    <>
      {/* Navigation Header */}
      <header className="navbar">
        <div className="container nav-container">
          <div className="brand-logo" onClick={() => setActiveTab('transparency')}>
            <div className="brand-icon-wrapper">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                <path d="m9 12 2 2 4-4"/>
              </svg>
            </div>
            <div className="brand-text">
              Impact<span>Graph</span>
            </div>
            <span className="brand-badge">Security Hub</span>
          </div>

          <nav>
            <ul className="nav-links">
              <li>
                <button
                  id="nav-transparency"
                  className={`nav-btn ${activeTab === 'transparency' ? 'active' : ''}`}
                  onClick={() => setActiveTab('transparency')}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
                  Public Transparency
                </button>
              </li>
              <li>
                <button
                  id="nav-my-activity"
                  className={`nav-btn ${activeTab === 'my-activity' ? 'active' : ''}`}
                  onClick={() => setActiveTab('my-activity')}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                  My Activity
                </button>
              </li>
              <li>
                <button
                  id="nav-sandbox"
                  className={`nav-btn ${activeTab === 'sandbox' ? 'active' : ''}`}
                  onClick={() => setActiveTab('sandbox')}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
                  Security Sandbox
                </button>
              </li>
              <li>
                <button
                  id="nav-admin"
                  className={`nav-btn ${activeTab === 'admin' ? 'active' : ''}`}
                  onClick={() => setActiveTab('admin')}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                  Admin Audit
                </button>
              </li>
            </ul>
          </nav>

          <div className="nav-actions">
            {/* Backend Health Status Pill */}
            <div className="health-pill" title="Backend Health Check (/api/health)" onClick={checkHealth}>
              <span className={`health-dot ${health.status === 'UP' ? 'up' : 'down'}`}></span>
              <span>API: {health.status} {health.latency ? `(${health.latency}ms)` : ''}</span>
            </div>

            {currentUser ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <span className={`role-pill ${currentUser.role === 'ADMIN' ? 'admin' : 'developer'}`}>
                  {currentUser.role}
                </span>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  {currentUser.email}
                </span>
                <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
                  Logout
                </button>
              </div>
            ) : (
              <button
                id="btn-open-auth"
                className="btn btn-primary btn-sm"
                onClick={() => { setAuthMode('login'); setAuthModalOpen(true); }}
              >
                Sign In / Register
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="container" style={{ flex: 1, paddingBottom: '3rem' }}>
        {/* Hero Section */}
        <section className="hero-section">
          <div className="hero-pill">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            Zero-Trust CNS Security &amp; Data Transparency
          </div>
          <h1 className="hero-title">
            Enterprise Security &amp; <span className="gradient-text">Transparency Center</span>
          </h1>
          <p className="hero-subtitle">
            Stateless JWT authentication (HS512), cost-factor 12 BCrypt hashing, strict role isolation,
            and an append-only immutable audit trail that prevents unauthorized tampering.
          </p>

          {/* Metrics Grid */}
          <div className="metrics-grid">
            <div className="metric-card">
              <div className="metric-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              </div>
              <div className="metric-value">HS512</div>
              <div className="metric-label">Stateless JWT</div>
              <div className="metric-desc">Zero session state in server memory; cryptographic signature verified per request.</div>
            </div>

            <div className="metric-card">
              <div className="metric-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 14.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0zm0-4.5a1 1 0 0 1-2 0V7a1 1 0 0 1 2 0z"/></svg>
              </div>
              <div className="metric-value">BCrypt-12</div>
              <div className="metric-label">Credential Defense</div>
              <div className="metric-desc">High work factor password hashing protects against rainbow table and brute force attacks.</div>
            </div>

            <div className="metric-card">
              <div className="metric-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
              </div>
              <div className="metric-value">Read-Only</div>
              <div className="metric-label">Audit Immutability</div>
              <div className="metric-desc">Audit logs cannot be updated or deleted via API. Disallowed mutations return 405 Method Not Allowed.</div>
            </div>

            <div className="metric-card">
              <div className="metric-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              </div>
              <div className="metric-value">RBAC + IDOR</div>
              <div className="metric-label">Scope Isolation</div>
              <div className="metric-desc">Strict project membership enforcement blocks horizontal privilege escalation and unauthorized reads.</div>
            </div>
          </div>
        </section>

        {/* =========================================================================
            TAB 1: PUBLIC TRANSPARENCY
            ========================================================================= */}
        {activeTab === 'transparency' && (
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title-group">
                <h2>Public Data Transparency Portal</h2>
                <p>Real-time disclosure of collected metadata, infrastructure posture, access policies, and retention schedules.</p>
              </div>
              <button className="btn btn-secondary btn-sm" onClick={fetchPublicTransparency}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>
                Refresh Data
              </button>
            </div>

            {/* Category Cards */}
            <h3 style={{ fontSize: '1.15rem', marginBottom: '1rem', color: 'var(--brand-cyan)' }}>
              1. Categories of Data Collected
            </h3>
            <div className="category-grid" style={{ marginBottom: '2.5rem' }}>
              {transparency.dataCollected?.map((cat, idx) => (
                <div key={idx} className="transparency-card">
                  <div className="card-category-header">
                    <span className="category-badge">{cat.category}</span>
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Fields Collected:</div>
                  <div className="field-tag-list">
                    {cat.fields?.map((field, fIdx) => (
                      <span key={fIdx} className="field-tag">{field}</span>
                    ))}
                  </div>
                  <div className="purpose-note">
                    <strong>Purpose:</strong> {cat.purpose}
                  </div>
                </div>
              ))}
            </div>

            {/* Data Storage & Infrastructure */}
            <h3 style={{ fontSize: '1.15rem', marginBottom: '1rem', color: 'var(--brand-indigo)' }}>
              2. Data Storage &amp; Infrastructure Commitments
            </h3>
            <div className="category-grid" style={{ marginBottom: '2.5rem' }}>
              <div className="transparency-card">
                <h4 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Database Engine</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  {transparency.dataStorage?.database} (relational persistence with ACID compliance)
                </p>
                <div className="purpose-note">
                  <strong>Location:</strong> {transparency.dataStorage?.location}
                </div>
              </div>

              <div className="transparency-card">
                <h4 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Storage Encryption</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  {transparency.dataStorage?.encryption}
                </p>
                <div className="purpose-note">
                  <strong>Integrity:</strong> Application protects sensitive passwords using irreversible salted BCrypt-12.
                </div>
              </div>

              <div className="transparency-card">
                <h4 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Backups &amp; Continuity</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  {transparency.dataStorage?.backups}
                </p>
                <div className="purpose-note">
                  <strong>Graph Analysis Status:</strong> {transparency.graphAnalysis?.status}
                </div>
              </div>
            </div>

            {/* Access Control Matrix */}
            <h3 style={{ fontSize: '1.15rem', marginBottom: '1rem', color: 'var(--brand-violet)' }}>
              3. Role-Based Access Control (RBAC) Matrix
            </h3>
            <div className="audit-table-wrapper" style={{ marginBottom: '2.5rem' }}>
              <table className="matrix-table">
                <thead>
                  <tr>
                    <th>Role</th>
                    <th>Permissions &amp; Capabilities</th>
                    <th>Restrictions &amp; Boundaries</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><span className="role-pill admin">ADMIN</span></td>
                    <td>
                      Create, update, and delete projects; grant and revoke project memberships; inspect entire system audit log; bootstrap admin account.
                    </td>
                    <td>
                      Cannot delete or alter existing audit event records via the API (append-only enforcement).
                    </td>
                  </tr>
                  <tr>
                    <td><span className="role-pill developer">DEVELOPER</span></td>
                    <td>
                      Access projects to which membership has been explicitly granted by an ADMIN; view personal audit activity via <code>/api/transparency/my-activity</code>.
                    </td>
                    <td>
                      Cannot view projects without granted membership (IDOR protection); cannot view global audit trails or assign roles.
                    </td>
                  </tr>
                  <tr>
                    <td><span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>PUBLIC / ANONYMOUS</span></td>
                    <td>
                      Access <code>/api/health</code>, <code>/api/transparency/public</code>, register new account, login to receive JWT.
                    </td>
                    <td>
                      Strictly denied (401 Unauthorized) from all project workspaces and protected endpoints.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Data Retention */}
            <h3 style={{ fontSize: '1.15rem', marginBottom: '1rem', color: '#10b981' }}>
              4. Data Retention Policy Disclosures
            </h3>
            <div className="category-grid">
              <div className="transparency-card">
                <h4 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Audit Event History</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  {transparency.dataRetention?.auditEvents}
                </p>
              </div>
              <div className="transparency-card">
                <h4 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>User Account Lifecycle</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  {transparency.dataRetention?.userAccounts}
                </p>
              </div>
              <div className="transparency-card">
                <h4 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Current Limitations</h4>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  {transparency.dataRetention?.limitation}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* =========================================================================
            TAB 2: MY ACTIVITY (AUTHENTICATED)
            ========================================================================= */}
        {activeTab === 'my-activity' && (
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title-group">
                <h2>User Activity Transparency</h2>
                <p>Review the exact audit logs and security telemetry recorded for your account.</p>
              </div>
              {currentUser && (
                <button className="btn btn-secondary btn-sm" onClick={fetchMyActivity}>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>
                  Refresh Activity
                </button>
              )}
            </div>

            {!currentUser ? (
              <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
                <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: 'rgba(99,102,241,0.1)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--brand-indigo)', marginBottom: '1.25rem' }}>
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                </div>
                <h3 style={{ fontSize: '1.3rem', marginBottom: '0.5rem' }}>Authentication Required</h3>
                <p style={{ color: 'var(--text-secondary)', maxWidth: '460px', margin: '0 auto 1.5rem' }}>
                  Sign in to inspect your user account telemetry, authenticated actions, and personal audit events.
                </p>
                <button className="btn btn-primary" onClick={() => { setAuthMode('login'); setAuthModalOpen(true); }}>
                  Sign In / Create Account
                </button>
              </div>
            ) : (
              <>
                <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', marginBottom: '1.5rem', background: 'rgba(255,255,255,0.02)', padding: '1rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>User ID:</span>
                    <div style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{currentUser.userId}</div>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Email:</span>
                    <div style={{ fontSize: '0.88rem' }}>{currentUser.email}</div>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Assigned Role:</span>
                    <div><span className={`role-pill ${currentUser.role === 'ADMIN' ? 'admin' : 'developer'}`}>{currentUser.role}</span></div>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Total Events Recorded:</span>
                    <div style={{ fontWeight: 700, fontSize: '0.95rem', color: 'var(--brand-cyan)' }}>{myActivity?.totalEvents ?? 0}</div>
                  </div>
                </div>

                {activityLoading ? (
                  <div style={{ textAlign: 'center', padding: '2rem' }}>Loading activity logs...</div>
                ) : myActivity?.recentActivity?.length > 0 ? (
                  <div className="audit-table-wrapper">
                    <table className="audit-table">
                      <thead>
                        <tr>
                          <th>Timestamp</th>
                          <th>Event Type</th>
                          <th>Outcome</th>
                          <th>Resource ID</th>
                          <th>IP Address</th>
                          <th>Details</th>
                        </tr>
                      </thead>
                      <tbody>
                        {myActivity.recentActivity.map((evt) => (
                          <tr key={evt.id}>
                            <td style={{ color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                              {new Date(evt.timestamp).toLocaleString()}
                            </td>
                            <td>
                              <span className={getEventBadgeClass(evt.eventType)}>{evt.eventType}</span>
                            </td>
                            <td>
                              <span style={{ color: evt.outcome === 'SUCCESS' ? 'var(--color-success)' : 'var(--color-danger)', fontWeight: 600 }}>
                                {evt.outcome}
                              </span>
                            </td>
                            <td style={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>
                              {evt.resourceId ? evt.resourceId.slice(0, 8) + '...' : '—'}
                            </td>
                            <td style={{ color: 'var(--text-muted)' }}>{evt.ipAddress || '127.0.0.1'}</td>
                            <td style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>
                              {evt.details ? JSON.stringify(evt.details) : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                    No recent audit events found for your account yet.
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* =========================================================================
            TAB 3: SECURITY SANDBOX & VERIFIER
            ========================================================================= */}
        {activeTab === 'sandbox' && (
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title-group">
                <h2>Security Verification Sandbox</h2>
                <p>Execute live API assertions against the backend to verify 200, 401, 403, and 405 defenses.</p>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
              <div className="metric-card" style={{ cursor: 'pointer' }} onClick={() => runSandboxTest('Health Check', '/api/health', 'GET', 'none')}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--color-success)', fontWeight: 700 }}>GET /api/health</span>
                  <span className="category-badge">Public (200)</span>
                </div>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                  Tests unauthenticated public endpoint. Expected response is HTTP 200 with UP status.
                </p>
                <button className="btn btn-secondary btn-sm" style={{ marginTop: '0.75rem' }} disabled={sandboxLoading}>
                  Run Assertion →
                </button>
              </div>

              <div className="metric-card" style={{ cursor: 'pointer' }} onClick={() => runSandboxTest('Unauthenticated Request', '/api/projects', 'GET', 'none')}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--color-warning)', fontWeight: 700 }}>GET /api/projects</span>
                  <span className="category-badge" style={{ background: 'var(--color-warning-bg)', color: 'var(--color-warning)' }}>No Token (401)</span>
                </div>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                  Attempts access to projects without a Bearer token. Verifies stateless JWT filter blocks with 401.
                </p>
                <button className="btn btn-secondary btn-sm" style={{ marginTop: '0.75rem' }} disabled={sandboxLoading}>
                  Run Assertion →
                </button>
              </div>

              <div className="metric-card" style={{ cursor: 'pointer' }} onClick={() => runSandboxTest('Audit Immutability', '/api/audit/events', 'POST', 'none')}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--color-danger)', fontWeight: 700 }}>POST /api/audit/events</span>
                  <span className="category-badge" style={{ background: 'var(--color-danger-bg)', color: 'var(--color-danger)' }}>Immutability (405)</span>
                </div>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                  Attempts mutation on read-only audit endpoints. Verifies server responds with 405 Method Not Allowed.
                </p>
                <button className="btn btn-secondary btn-sm" style={{ marginTop: '0.75rem' }} disabled={sandboxLoading}>
                  Run Assertion →
                </button>
              </div>

              <div className="metric-card" style={{ cursor: 'pointer' }} onClick={() => runSandboxTest('Admin Audit Access', '/api/audit/events', 'GET', 'bearer')}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <span style={{ color: 'var(--brand-violet)', fontWeight: 700 }}>GET /api/audit/events</span>
                  <span className="category-badge" style={{ background: 'rgba(139, 92, 246, 0.2)', color: '#c4b5fd' }}>Role Guard</span>
                </div>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                  Sends request with current token. Returns 200 if ADMIN, 403 Forbidden if DEVELOPER, or 401 if unauthenticated.
                </p>
                <button className="btn btn-secondary btn-sm" style={{ marginTop: '0.75rem' }} disabled={sandboxLoading}>
                  Run Assertion →
                </button>
              </div>
            </div>

            {/* Sandbox Console Output */}
            {sandboxResponse && (
              <div className="sandbox-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255,255,255,0.08)', paddingBottom: '0.5rem' }}>
                  <div>
                    <strong>Test:</strong> {sandboxResponse.testName} &nbsp;|&nbsp; <code>{sandboxResponse.method} {sandboxResponse.path}</code>
                  </div>
                  <div>
                    <span style={{
                      fontWeight: 700,
                      color: sandboxResponse.status >= 200 && sandboxResponse.status < 300 ? 'var(--color-success)' :
                             sandboxResponse.status === 405 ? 'var(--color-warning)' :
                             sandboxResponse.status === 401 || sandboxResponse.status === 403 ? '#60a5fa' : 'var(--color-danger)'
                    }}>
                      Status: {sandboxResponse.status} {sandboxResponse.statusText}
                    </span> &nbsp;
                    <span style={{ color: 'var(--text-muted)' }}>({sandboxResponse.duration})</span>
                  </div>
                </div>
                <pre className="sandbox-response-box">
                  {sandboxResponse.body}
                </pre>
              </div>
            )}
          </div>
        )}

        {/* =========================================================================
            TAB 4: ADMIN AUDIT & SECURITY CONSOLE
            ========================================================================= */}
        {activeTab === 'admin' && (
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title-group">
                <h2>Admin Security &amp; Audit Log Center</h2>
                <p>Read-only global audit trail capturing all identity, membership, and workspace security events.</p>
              </div>
              {currentUser?.role === 'ADMIN' && (
                <div style={{ display: 'flex', gap: '0.75rem' }}>
                  <select
                    className="form-select"
                    style={{ width: 'auto', padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
                    value={auditFilterType}
                    onChange={(e) => setAuditFilterType(e.target.value)}
                  >
                    <option value="ALL">All Event Types</option>
                    <option value="LOGIN">LOGIN</option>
                    <option value="LOGIN_FAILURE">LOGIN_FAILURE</option>
                    <option value="REGISTER">REGISTER</option>
                    <option value="PROJECT_CREATED">PROJECT_CREATED</option>
                    <option value="MEMBERSHIP_GRANTED">MEMBERSHIP_GRANTED</option>
                    <option value="ACCESS_DENIED">ACCESS_DENIED</option>
                  </select>
                  <button className="btn btn-secondary btn-sm" onClick={fetchAdminAudit}>
                    Refresh
                  </button>
                </div>
              )}
            </div>

            {currentUser?.role !== 'ADMIN' ? (
              <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
                <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: 'rgba(239,68,68,0.1)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-danger)', marginBottom: '1.25rem' }}>
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>
                </div>
                <h3 style={{ fontSize: '1.3rem', marginBottom: '0.5rem' }}>ADMIN Authorization Required</h3>
                <p style={{ color: 'var(--text-secondary)', maxWidth: '460px', margin: '0 auto 1.5rem' }}>
                  This view requires the <code>ADMIN</code> role. The first registered user in ImpactGraph is automatically promoted to <code>ADMIN</code>.
                </p>
                <button className="btn btn-primary" onClick={() => { setAuthMode('register'); setAuthModalOpen(true); }}>
                  Register / Sign In as Admin
                </button>
              </div>
            ) : (
              <>
                <div style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                    Showing {adminAuditEvents.length} recorded events (Tamper-evident, Append-only)
                  </span>
                  <span className="category-badge" style={{ background: 'rgba(16,185,129,0.1)', color: 'var(--color-success)' }}>
                    ✓ Immutable Audit Storage Active
                  </span>
                </div>

                {auditLoading ? (
                  <div style={{ textAlign: 'center', padding: '2rem' }}>Fetching system audit trail...</div>
                ) : adminAuditEvents.length > 0 ? (
                  <div className="audit-table-wrapper">
                    <table className="audit-table">
                      <thead>
                        <tr>
                          <th>Timestamp</th>
                          <th>Event Type</th>
                          <th>Actor</th>
                          <th>Outcome</th>
                          <th>Project / Resource</th>
                          <th>IP Address</th>
                          <th>Details</th>
                        </tr>
                      </thead>
                      <tbody>
                        {adminAuditEvents.map((evt) => (
                          <tr key={evt.id}>
                            <td style={{ color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                              {new Date(evt.timestamp).toLocaleString()}
                            </td>
                            <td>
                              <span className={getEventBadgeClass(evt.eventType)}>{evt.eventType}</span>
                            </td>
                            <td style={{ fontWeight: 500 }}>{evt.actorEmail || 'anonymous'}</td>
                            <td>
                              <span style={{ color: evt.outcome === 'SUCCESS' ? 'var(--color-success)' : 'var(--color-danger)', fontWeight: 600 }}>
                                {evt.outcome}
                              </span>
                            </td>
                            <td style={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>
                              {evt.projectId ? `Proj: ${evt.projectId.slice(0, 6)}...` : evt.resourceId ? `Res: ${evt.resourceId.slice(0, 6)}...` : '—'}
                            </td>
                            <td style={{ color: 'var(--text-muted)' }}>{evt.ipAddress || '127.0.0.1'}</td>
                            <td style={{ color: 'var(--text-secondary)', fontSize: '0.78rem' }}>
                              {evt.details ? JSON.stringify(evt.details) : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                    No audit events match the selected criteria.
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="footer">
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <strong>ImpactGraph</strong> &mdash; Software Dependency &amp; Change Impact Platform
          </div>
          <div>
            Branch: <code>feat/security-audit</code> &nbsp;|&nbsp; Spring Boot 3.2.5 &nbsp;|&nbsp; React + Vite
          </div>
        </div>
      </footer>

      {/* Authentication Modal */}
      {authModalOpen && (
        <div className="modal-overlay" onClick={() => setAuthModalOpen(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <h3 style={{ fontSize: '1.25rem' }}>
                {authMode === 'login' ? 'Sign In to ImpactGraph' : 'Create an Account'}
              </h3>
              <button
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '1.25rem' }}
                onClick={() => setAuthModalOpen(false)}
              >
                ✕
              </button>
            </div>

            {authMode === 'register' && (
              <div style={{ background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.2)', padding: '0.75rem', borderRadius: 'var(--radius-md)', fontSize: '0.8rem', color: 'var(--brand-indigo)', marginBottom: '1rem' }}>
                <strong>Note:</strong> The first user registered in ImpactGraph automatically receives the <strong>ADMIN</strong> role. Subsequent users receive <strong>DEVELOPER</strong>.
              </div>
            )}

            <form onSubmit={handleAuthSubmit}>
              {authMode === 'register' && (
                <div className="form-group">
                  <label className="form-label">Display Name</label>
                  <input
                    type="text"
                    required
                    className="form-input"
                    placeholder="Alice Engineer"
                    value={authForm.displayName}
                    onChange={(e) => setAuthForm({ ...authForm, displayName: e.target.value })}
                  />
                </div>
              )}

              <div className="form-group">
                <label className="form-label">Email Address</label>
                <input
                  type="email"
                  required
                  className="form-input"
                  placeholder="name@company.com"
                  value={authForm.email}
                  onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Password</label>
                <input
                  type="password"
                  required
                  className="form-input"
                  placeholder="••••••••••••"
                  value={authForm.password}
                  onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })}
                />
              </div>

              {authError && <div className="form-error" style={{ marginBottom: '1rem' }}>{authError}</div>}

              <button type="submit" className="btn btn-primary" style={{ width: '100%', marginBottom: '1rem' }} disabled={authLoading}>
                {authLoading ? 'Processing...' : authMode === 'login' ? 'Sign In' : 'Register Account'}
              </button>
            </form>

            <div style={{ textAlign: 'center', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              {authMode === 'login' ? (
                <>
                  Don't have an account yet?{' '}
                  <a href="#register" onClick={(e) => { e.preventDefault(); setAuthMode('register'); setAuthError(''); }}>
                    Register here
                  </a>
                </>
              ) : (
                <>
                  Already registered?{' '}
                  <a href="#login" onClick={(e) => { e.preventDefault(); setAuthMode('login'); setAuthError(''); }}>
                    Sign in here
                  </a>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
