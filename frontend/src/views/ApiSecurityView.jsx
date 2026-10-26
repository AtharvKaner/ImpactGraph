import React, { useState, useMemo, useEffect } from 'react';
import {
  IconSearch,
  IconClear,
  IconShieldAlert,
  IconCheckShield,
  IconCode,
  IconRefresh,
} from '../components/Icons';
import { fetchApiInventory } from '../services/impactService';

export function ApiSecurityView() {
  const [inventory, setInventory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [methodFilter, setMethodFilter] = useState('ALL');
  const [authFilter, setAuthFilter] = useState('ALL');

  const methods = ['ALL', 'GET', 'POST', 'PUT', 'DELETE'];
  const authTypes = ['ALL', 'PUBLIC', 'AUTHENTICATED', 'ROLE_BASED'];

  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    async function loadData() {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchApiInventory();
        if (!ignore) setInventory(data);
      } catch (err) {
        if (!ignore) setError(err.message || 'Failed to load API inventory');
      } finally {
        if (!ignore) setLoading(false);
      }
    }
    loadData();
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const filteredApis = useMemo(() => {
    return inventory.filter((item) => {
      const matchesSearch = !searchTerm.trim() ||
        item.path.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (item.controllerName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (item.handlerMethod || '').toLowerCase().includes(searchTerm.toLowerCase());
      const matchesMethod = methodFilter === 'ALL' || item.method === methodFilter;
      const matchesAuth = authFilter === 'ALL' || item.authRequirement === authFilter;
      return matchesSearch && matchesMethod && matchesAuth;
    });
  }, [inventory, searchTerm, methodFilter, authFilter]);

  const publicCount = inventory.filter((i) => i.authRequirement === 'PUBLIC').length;
  const roleCount = inventory.filter((i) => i.authRequirement === 'ROLE_BASED').length;
  const authenticatedCount = inventory.filter((i) => i.authRequirement === 'AUTHENTICATED').length;

  return (
    <div className="view-container api-security-view">
      <div className="view-header-row">
        <div>
          <h2 className="view-title">API Security Inventory & Access Control Matrix</h2>
          <p className="view-subtitle">
            Static AST inspection of Spring Boot REST controllers, URL matchers in SecurityConfig, and method-level @PreAuthorize enforcement
          </p>
        </div>
        <button type="button" className="btn-secondary" onClick={() => setReloadKey((k) => k + 1)} disabled={loading}>
          <IconRefresh size={14} className={loading ? 'spin-icon' : ''} />
          <span>Refresh Inventory</span>
        </button>
      </div>

      {/* KPI Header Cards */}
      <div className="overview-kpi-grid">
        <div className="kpi-card">
          <div className="kpi-icon-wrap icon-blue">
            <IconCode size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{inventory.length}</span>
            <span className="kpi-label">Total Endpoints</span>
            <span className="kpi-hint">Scanned across all controllers</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-icon-wrap icon-purple">
            <IconShieldAlert size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{roleCount}</span>
            <span className="kpi-label">Role-Restricted (RBAC)</span>
            <span className="kpi-hint">Requires ADMIN role</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-icon-wrap icon-green">
            <IconCheckShield size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{authenticatedCount}</span>
            <span className="kpi-label">Authenticated (JWT)</span>
            <span className="kpi-hint">Requires valid Bearer token</span>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-icon-wrap icon-amber">
            <IconCode size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{publicCount}</span>
            <span className="kpi-label">Public Endpoints</span>
            <span className="kpi-hint">Health, Login, Public Transparency</span>
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="table-controls-bar">
        <div className="filter-search-box">
          <IconSearch size={14} className="filter-search-icon" />
          <input
            type="text"
            className="filter-search-input"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Filter by route path (/api/...), controller, or method..."
          />
          {searchTerm && (
            <button
              type="button"
              className="filter-search-clear"
              onClick={() => setSearchTerm('')}
            >
              <IconClear size={12} />
            </button>
          )}
        </div>

        <div className="filter-dropdown-group">
          <span className="filter-label">HTTP Method:</span>
          <div className="segmented-toggle">
            {methods.map((m) => (
              <button
                key={m}
                type="button"
                className={`toggle-btn ${methodFilter === m ? 'active' : ''}`}
                onClick={() => setMethodFilter(m)}
              >
                {m}
              </button>
            ))}
          </div>
        </div>

        <div className="filter-dropdown-group">
          <span className="filter-label">Auth Level:</span>
          <div className="segmented-toggle">
            {authTypes.map((a) => (
              <button
                key={a}
                type="button"
                className={`toggle-btn ${authFilter === a ? 'active' : ''}`}
                onClick={() => setAuthFilter(a)}
              >
                {a}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Inventory Table */}
      <div className="table-card">
        {loading ? (
          <div className="table-loading-box">
            <span className="spinner" />
            <p>Scanning Spring Boot controllers and compiling security rules...</p>
          </div>
        ) : error ? (
          <div className="table-error-box">
            <span>Error loading API inventory: {error}</span>
          </div>
        ) : (
          <table className="api-inventory-table">
            <thead>
              <tr>
                <th>Method</th>
                <th>Endpoint Path</th>
                <th>Controller & Handler</th>
                <th>Auth Level</th>
                <th>Required Role</th>
                <th>Security Enforcement</th>
                <th>Risk Rating</th>
              </tr>
            </thead>
            <tbody>
              {filteredApis.map((api, idx) => (
                <tr key={`${api.method}-${api.path}-${idx}`}>
                  <td>
                    <span className={`http-method-tag method-${api.method.toLowerCase()}`}>
                      {api.method}
                    </span>
                  </td>
                  <td>
                    <code className="endpoint-path-code">{api.path}</code>
                  </td>
                  <td>
                    <div className="handler-meta">
                      <span className="controller-name">{api.controllerName}</span>
                      <span className="handler-name">#{api.handlerMethod}()</span>
                    </div>
                  </td>
                  <td>
                    <span className={`auth-badge-pill auth-${api.authRequirement.toLowerCase()}`}>
                      {api.authRequirement}
                    </span>
                  </td>
                  <td>
                    {api.requiredRole ? (
                      <span className="role-tag tag-admin">{api.requiredRole}</span>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                  <td>
                    <span className="security-source-text" title={api.securitySource}>
                      {api.securitySource}
                    </span>
                  </td>
                  <td>
                    <span className={`risk-pill pill-${api.riskLevel.toLowerCase()}`}>
                      {api.riskLevel}
                    </span>
                  </td>
                </tr>
              ))}
              {filteredApis.length === 0 && (
                <tr>
                  <td colSpan={7} className="table-empty-cell">
                    No API endpoints matched your query.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
