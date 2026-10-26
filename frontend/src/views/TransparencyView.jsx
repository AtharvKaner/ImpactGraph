import React, { useState, useEffect } from 'react';
import {
  IconCheckShield,
  IconShieldAlert,
  IconRefresh,
  IconCode,
} from '../components/Icons';
import { fetchPublicTransparency } from '../services/impactService';

export function TransparencyView() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    async function loadData() {
      setLoading(true);
      setError(null);
      try {
        const res = await fetchPublicTransparency();
        if (!ignore) setData(res);
      } catch (err) {
        if (!ignore) setError(err.message || 'Failed to fetch public transparency disclosure');
      } finally {
        if (!ignore) setLoading(false);
      }
    }
    loadData();
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  return (
    <div className="view-container transparency-view">
      {/* Header */}
      <div className="view-header-row">
        <div>
          <div className="title-with-badge">
            <h2 className="view-title">Public Transparency & Data Governance Portal</h2>
            <span className="badge badge-success">Publicly Auditable Contract</span>
          </div>
          <p className="view-subtitle">
            Formal disclosure of data collection, storage security, role-based boundaries, and blast-radius graph boundaries.
            Endpoint: <code className="inline-code">GET /api/transparency/public</code> (Unauthenticated).
          </p>
        </div>
        <div className="view-header-actions">
          <button
            type="button"
            className="btn-secondary-action"
            onClick={() => setReloadKey((k) => k + 1)}
            disabled={loading}
          >
            <IconRefresh size={14} />
            <span>Refresh Disclosure</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="card-panel panel-empty-state">
          <div className="spinner-inline" />
          <span>Loading transparency statements...</span>
        </div>
      ) : error ? (
        <div className="card-panel panel-empty-state text-danger">
          <IconShieldAlert size={28} />
          <p>{error}</p>
        </div>
      ) : (
        <div className="transparency-grid">
          {/* Card 1: Data Categories Collected */}
          <div className="card-panel transparency-card">
            <div className="card-header-styled">
              <div className="icon-badge icon-badge-blue">
                <IconCode size={18} />
              </div>
              <div>
                <h3 className="card-title">1. Data Categories Collected</h3>
                <span className="text-muted text-xs">Exhaustive list of data ingested by ImpactGraph</span>
              </div>
            </div>
            <div className="card-body-content">
              {data?.dataCollected?.map((item, idx) => (
                <div key={idx} className="transparency-item">
                  <div className="item-header">
                    <strong>{item.category}</strong>
                    <span className="badge badge-neutral text-xs">{item.purpose}</span>
                  </div>
                  <ul className="field-bullet-list">
                    {item.fields.map((f, fIdx) => (
                      <li key={fIdx}>{f}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </div>

          {/* Card 2: Data Storage & Encryption */}
          <div className="card-panel transparency-card">
            <div className="card-header-styled">
              <div className="icon-badge icon-badge-purple">
                <IconCheckShield size={18} />
              </div>
              <div>
                <h3 className="card-title">2. Storage Architecture & Encryption</h3>
                <span className="text-muted text-xs">Persistence mechanisms and crypto controls</span>
              </div>
            </div>
            <div className="card-body-content">
              <div className="meta-list">
                <div className="meta-row">
                  <span className="meta-key">Primary Database</span>
                  <span className="meta-value">{data?.dataStorage?.database || 'PostgreSQL'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Deployment Location</span>
                  <span className="meta-value">{data?.dataStorage?.location || 'Configured via DB_URL'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Encryption Policy</span>
                  <span className="meta-value">{data?.dataStorage?.encryption || 'Database & Storage Level'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Backup Cadence</span>
                  <span className="meta-value">{data?.dataStorage?.backups || 'Managed by infra operations'}</span>
                </div>
              </div>

              <div className="transparency-callout" style={{ marginTop: '1.25rem' }}>
                <h4 className="callout-title">Static Parsing Guarantee</h4>
                <p className="text-muted text-xs">
                  ImpactGraph uses safe Java AST parsing (JavaParser / Lexical scan). It NEVER executes arbitrary code from target repositories to inspect them.
                </p>
              </div>
            </div>
          </div>

          {/* Card 3: RBAC & Tenant Isolation */}
          <div className="card-panel transparency-card">
            <div className="card-header-styled">
              <div className="icon-badge icon-badge-amber">
                <IconShieldAlert size={18} />
              </div>
              <div>
                <h3 className="card-title">3. Access Control & RBAC Matrix</h3>
                <span className="text-muted text-xs">Security boundaries enforced by Spring Security</span>
              </div>
            </div>
            <div className="card-body-content">
              <div className="rbac-role-card">
                <div className="role-header">
                  <span className="badge badge-purple">ADMIN Role</span>
                </div>
                <p className="text-muted text-xs" style={{ marginTop: '0.4rem' }}>
                  {data?.accessControl?.ADMIN ||
                    'Full platform authority: manage projects, grant/revoke memberships, view complete organization audit logs, configure webhooks.'}
                </p>
              </div>

              <div className="rbac-role-card" style={{ marginTop: '0.75rem' }}>
                <div className="role-header">
                  <span className="badge badge-neutral">DEVELOPER Role</span>
                </div>
                <p className="text-muted text-xs" style={{ marginTop: '0.4rem' }}>
                  {data?.accessControl?.DEVELOPER ||
                    'Tenant-isolated: Access restricted strictly to projects where explicit membership is granted. Cannot view org-wide logs or manage users.'}
                </p>
              </div>

              <div className="transparency-callout" style={{ marginTop: '1rem' }}>
                <h4 className="callout-title">Tenant Isolation Verification</h4>
                <p className="text-muted text-xs">
                  Any attempt by a DEVELOPER to query or modify a project they are not a member of triggers an <code className="inline-code">ACCESS_DENIED</code> security audit event and returns an HTTP 403 Forbidden.
                </p>
              </div>
            </div>
          </div>

          {/* Card 4: Retention & Graph Engine */}
          <div className="card-panel transparency-card">
            <div className="card-header-styled">
              <div className="icon-badge icon-badge-green">
                <IconCheckShield size={18} />
              </div>
              <div>
                <h3 className="card-title">4. Graph Engine & Data Retention</h3>
                <span className="text-muted text-xs">Lifecycle policies and algorithmic behavior</span>
              </div>
            </div>
            <div className="card-body-content">
              <div className="meta-list">
                <div className="meta-row">
                  <span className="meta-key">Engine Description</span>
                  <span className="meta-value">{data?.graphAnalysis?.description || 'Change-impact graph analysis'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Cycle Detection</span>
                  <span className="meta-value">{data?.graphAnalysis?.status || 'Tarjan Strongly Connected Components'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Audit Retention</span>
                  <span className="meta-value">{data?.dataRetention?.auditEvents || 'Indefinite compliance retention'}</span>
                </div>
                <div className="meta-row">
                  <span className="meta-key">Account Deletion</span>
                  <span className="meta-value">{data?.dataRetention?.userAccounts || 'Manual administrator de-provisioning'}</span>
                </div>
              </div>

              <div className="transparency-callout" style={{ marginTop: '1.25rem' }}>
                <h4 className="callout-title">Data Privacy Assurance</h4>
                <p className="text-muted text-xs">
                  Public transparency endpoints never disclose passwords, JWT secrets, raw stack traces, or private repository source code.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
