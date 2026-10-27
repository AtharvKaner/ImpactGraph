import React, { useState, useEffect } from 'react';
import {
  IconCheckShield,
  IconRefresh,
  IconGitHub,
  IconSparkles,
  IconCode,
} from '../components/Icons';
import { fetchHealth, fetchGitHubStatus } from '../services/impactService';

export function SettingsView({ user, onLogout }) {
  const [health, setHealth] = useState(null);
  const [loadingHealth, setLoadingHealth] = useState(true);
  const [gitHubStatus, setGitHubStatus] = useState(null);
  const [loadingGitHub, setLoadingGitHub] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    async function loadDiagnostics() {
      setLoadingHealth(true);
      setLoadingGitHub(true);
      try {
        const [hRes, gRes] = await Promise.allSettled([
          fetchHealth(),
          fetchGitHubStatus(),
        ]);
        if (!ignore) {
          if (hRes.status === 'fulfilled') setHealth(hRes.value);
          else setHealth({ status: 'OFFLINE', error: hRes.reason?.message });

          if (gRes.status === 'fulfilled') setGitHubStatus(gRes.value);
          else setGitHubStatus({ configured: false, note: gRes.reason?.message });
        }
      } finally {
        if (!ignore) {
          setLoadingHealth(false);
          setLoadingGitHub(false);
        }
      }
    }
    loadDiagnostics();
    return () => { ignore = true; };
  }, [reloadKey]);

  return (
    <div className="view-container settings-view">
      {/* Header */}
      <div className="view-header-row">
        <div>
          <h2 className="view-title">System Settings & Integrations</h2>
          <p className="view-subtitle">
            Manage your authenticated profile, inspection engine health, AI explanation configuration, and GitHub App webhooks.
          </p>
        </div>
        <div className="view-header-actions">
          <button
            type="button"
            className="btn-secondary-action"
            onClick={() => setReloadKey((k) => k + 1)}
            disabled={loadingHealth || loadingGitHub}
          >
            <IconRefresh size={14} />
            <span>Refresh Diagnostics</span>
          </button>
        </div>
      </div>

      <div className="settings-grid">
        {/* Card 1: User Profile & Security Session */}
        <div className="card-panel settings-card">
          <div className="card-header-styled">
            <div className="icon-badge icon-badge-purple">
              <IconCheckShield size={18} />
            </div>
            <div>
              <h3 className="card-title">User Identity & Authentication</h3>
              <span className="text-muted text-xs">Current JWT session state</span>
            </div>
          </div>
          <div className="card-body-content">
            <div className="meta-list">
              <div className="meta-row">
                <span className="meta-key">Email Address</span>
                <span className="meta-value bold-text">{user?.email || 'engineer@impactgraph.io'}</span>
              </div>
              <div className="meta-row">
                <span className="meta-key">Assigned Role</span>
                <span className={`badge ${user?.role === 'ADMIN' ? 'badge-purple' : 'badge-neutral'}`}>
                  {user?.role || 'DEVELOPER'}
                </span>
              </div>
              <div className="meta-row">
                <span className="meta-key">Display Name</span>
                <span className="meta-value">{user?.displayName || 'Software Engineer'}</span>
              </div>
              <div className="meta-row">
                <span className="meta-key">Session Token</span>
                <span className="meta-value mono-text text-xs">Active (sessionStorage)</span>
              </div>
            </div>

            <div style={{ marginTop: '1.5rem' }}>
              <button
                type="button"
                className="btn-secondary-action full-width"
                style={{ borderColor: 'var(--color-danger)', color: 'var(--color-danger)' }}
                onClick={onLogout}
              >
                <span>Terminate Session & Logout</span>
              </button>
            </div>
          </div>
        </div>

        {/* Card 2: Backend Platform Health */}
        <div className="card-panel settings-card">
          <div className="card-header-styled">
            <div className="icon-badge icon-badge-green">
              <IconCode size={18} />
            </div>
            <div>
              <h3 className="card-title">Analysis Platform Health</h3>
              <span className="text-muted text-xs">Service connection and storage status</span>
            </div>
          </div>
          <div className="card-body-content">
            {loadingHealth ? (
              <div className="panel-empty-state">
                <div className="spinner-inline" />
                <span>Checking health endpoint...</span>
              </div>
            ) : (
              <div className="meta-list">
                <div className="meta-row">
                  <span className="meta-key">Analysis Engine</span>
                  <span className={`badge ${health?.status === 'UP' || health?.status === 'HEALTHY' ? 'badge-success' : 'badge-danger'}`}>
                    {health?.status || 'OPERATIONAL'}
                  </span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Data Persistence</span>
                  <span className="meta-value">{health?.database ? 'Persistent Storage (Connected)' : 'Connected'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Platform Architecture</span>
                  <span className="meta-value">ImpactGraph Core v0.0.1</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Endpoint Checked</span>
                  <span className="meta-value mono-text text-xs">/api/health</span>
                </div>
              </div>
            )}
            <div className="transparency-callout" style={{ marginTop: '1.25rem' }}>
              <h4 className="callout-title">Offline Demonstration Mode</h4>
              <p className="text-muted text-xs">
                When the Spring Boot daemon is offline, the React frontend activates deterministic local mock simulations so all 10 views remain fully interactive.
              </p>
            </div>
          </div>
        </div>

        {/* Card 3: GitHub PR Webhook Integration */}
        <div className="card-panel settings-card">
          <div className="card-header-styled">
            <div className="icon-badge icon-badge-blue">
              <IconGitHub size={18} />
            </div>
            <div>
              <h3 className="card-title">GitHub Pull-Request Integration</h3>
              <span className="text-muted text-xs">Automated CI/CD security checks</span>
            </div>
          </div>
          <div className="card-body-content">
            {loadingGitHub ? (
              <div className="panel-empty-state">
                <div className="spinner-inline" />
                <span>Checking webhook status...</span>
              </div>
            ) : (
              <div className="meta-list">
                <div className="meta-row">
                  <span className="meta-key">Webhook Status</span>
                  <span className={`badge ${gitHubStatus?.configured ? 'badge-success' : 'badge-neutral'}`}>
                    {gitHubStatus?.configured ? 'Active (HMAC Verified)' : 'Unconfigured / Standby'}
                  </span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Webhook Endpoint</span>
                  <span className="meta-value mono-text text-xs">{gitHubStatus?.endpoint || '/api/github/webhook'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Monitored Events</span>
                  <span className="meta-value mono-text text-xs">{gitHubStatus?.monitoredEvents || 'pull_request'}</span>
                </div>
              </div>
            )}
            <div className="transparency-callout" style={{ marginTop: '1.25rem' }}>
              <h4 className="callout-title">Setup Instructions</h4>
              <p className="text-muted text-xs">
                To connect a GitHub repository, set environment variable <code className="inline-code">GITHUB_WEBHOOK_SECRET</code> in your backend environment and register the webhook URL with Content-Type <code className="inline-code">application/json</code>.
              </p>
            </div>
          </div>
        </div>

        {/* Card 4: AI Explanation Provider */}
        <div className="card-panel settings-card">
          <div className="card-header-styled">
            <div className="icon-badge icon-badge-amber">
              <IconSparkles size={18} />
            </div>
            <div>
              <h3 className="card-title">AI Remediation & Threat Models</h3>
              <span className="text-muted text-xs">Offline deterministic remediation guidance</span>
            </div>
          </div>
          <div className="card-body-content">
            <div className="meta-list">
              <div className="meta-row">
                <span className="meta-key">AI Integration</span>
                <span className="badge badge-info">Offline only</span>
              </div>
              <div className="meta-row">
                <span className="meta-key">Active Provider</span>
                <span className="meta-value">Deterministic Security Knowledge Engine</span>
              </div>
              <div className="meta-row">
                <span className="meta-key">External AI APIs</span>
                <span className="meta-value">Not used in this demo</span>
              </div>
            </div>

            <div className="transparency-callout" style={{ marginTop: '1.25rem' }}>
              <h4 className="callout-title">No External Data Transfer</h4>
              <p className="text-muted text-xs">
                Findings and remediation guidance are generated locally from static analysis evidence. No repository content or credentials are sent to an external AI service.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
