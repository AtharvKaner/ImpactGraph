import React, { useState, useEffect, useMemo } from 'react';
import {
  IconHistory,
  IconShieldAlert,
  IconAlertTriangle,
  IconRefresh,
  IconSearch,
} from '../components/Icons';
import { fetchAuditEvents, fetchMyActivity } from '../services/impactService';

export function AuditExplorerView({ user }) {
  const isAdmin = user?.role === 'ADMIN';
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [eventTypeFilter, setEventTypeFilter] = useState('ALL');

  const eventTypes = [
    'ALL',
    'LOGIN_SUCCESS',
    'LOGIN_FAILURE',
    'PROJECT_CREATED',
    'PROJECT_UPDATED',
    'MEMBERSHIP_GRANTED',
    'MEMBERSHIP_REVOKED',
    'ACCESS_DENIED',
    'SECURITY_SCAN_TRIGGERED',
  ];

  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    async function loadData() {
      setLoading(true);
      setError(null);
      try {
        if (isAdmin) {
          const res = await fetchAuditEvents({
            page,
            size: 15,
            eventType: eventTypeFilter === 'ALL' ? undefined : eventTypeFilter,
          });
          if (!ignore) {
            setEvents(res.content || []);
            setTotalPages(res.totalPages || 1);
            setTotalElements(res.totalElements || 0);
          }
        } else {
          const res = await fetchMyActivity({ page, size: 15 });
          if (!ignore) {
            setEvents(res.recentActivity || []);
            setTotalPages(res.totalPages || 1);
            setTotalElements(res.totalEvents || 0);
          }
        }
      } catch (err) {
        if (!ignore) setError(err.message || 'Failed to load audit logs.');
      } finally {
        if (!ignore) setLoading(false);
      }
    }
    loadData();
    return () => {
      ignore = true;
    };
  }, [page, eventTypeFilter, isAdmin, reloadKey]);

  const filteredEvents = useMemo(() => {
    if (!searchTerm.trim()) return events;
    const term = searchTerm.toLowerCase();
    return events.filter(
      (e) =>
        (e.eventType || '').toLowerCase().includes(term) ||
        (e.actorEmail || '').toLowerCase().includes(term) ||
        (e.description || '').toLowerCase().includes(term) ||
        (e.clientIp || '').toLowerCase().includes(term)
    );
  }, [events, searchTerm]);

  return (
    <div className="view-container audit-explorer-view">
      {/* Header */}
      <div className="view-header-row">
        <div>
          <div className="title-with-badge">
            <h2 className="view-title">Audit Explorer & Security Event Trail</h2>
            <span className={`badge ${isAdmin ? 'badge-purple' : 'badge-neutral'}`}>
              {isAdmin ? 'ADMIN: Full Platform Log' : 'DEVELOPER: My Activity'}
            </span>
          </div>
          <p className="view-subtitle">
            {isAdmin
              ? 'Complete immutable security event log including authentication, RBAC modifications, access denials, and API scans.'
              : 'Showing your authenticated actions and session audit trail. Full organization audit logs require ADMIN role.'}
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
            <span>Refresh Audit Log</span>
          </button>
        </div>
      </div>

      {!isAdmin && (
        <div className="feedback-banner feedback-info" style={{ marginBottom: '1.25rem' }}>
          <IconShieldAlert size={16} />
          <span>
            You are logged in as <strong>{user?.email || 'developer'}</strong> with role <strong>DEVELOPER</strong>.
            Backend RBAC enforces that organization-wide security logs are restricted to ADMIN users. Below is your personal activity log.
          </span>
        </div>
      )}

      {/* Main Table Panel */}
      <div className="card-panel">
        <div className="panel-header">
          <div className="panel-title-group">
            <span className="panel-title">Audit Ledger</span>
            <span className="badge badge-neutral">{totalElements} Total Recorded Events</span>
          </div>
          <div className="panel-filter-row">
            <div className="search-bar-inline">
              <IconSearch size={14} className="search-icon" />
              <input
                type="text"
                placeholder="Search actor, description, IP..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
            {isAdmin && (
              <select
                className="select-filter-compact"
                value={eventTypeFilter}
                onChange={(e) => {
                  setEventTypeFilter(e.target.value);
                  setPage(0);
                }}
              >
                {eventTypes.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            )}
          </div>
        </div>

        {loading ? (
          <div className="panel-empty-state">
            <div className="spinner-inline" />
            <span>Loading security events...</span>
          </div>
        ) : error ? (
          <div className="panel-empty-state text-danger">
            <IconAlertTriangle size={28} />
            <p>{error}</p>
          </div>
        ) : filteredEvents.length === 0 ? (
          <div className="panel-empty-state">
            <IconHistory size={28} />
            <p>No audit events match current filters.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Event Type</th>
                  <th>Outcome</th>
                  <th>Actor</th>
                  <th>Description</th>
                  <th>Client IP</th>
                </tr>
              </thead>
              <tbody>
                {filteredEvents.map((event, idx) => {
                  const isSuccess = event.outcome === 'SUCCESS';
                  return (
                    <tr key={event.id || idx}>
                      <td>
                        <span className="text-muted text-xs mono-text">
                          {event.timestamp ? new Date(event.timestamp).toLocaleString() : 'Recent'}
                        </span>
                      </td>
                      <td>
                        <span className="code-badge">{event.eventType}</span>
                      </td>
                      <td>
                        <span className={`badge ${isSuccess ? 'badge-success' : 'badge-danger'}`}>
                          {event.outcome}
                        </span>
                      </td>
                      <td>
                        <span className="user-email-text">{event.actorEmail || user?.email || 'System'}</span>
                      </td>
                      <td>
                        <span className="event-desc-text">{event.description}</span>
                      </td>
                      <td>
                        <span className="mono-text text-xs text-muted">{event.clientIp || '127.0.0.1'}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination controls */}
        {totalPages > 1 && (
          <div className="pagination-bar">
            <span className="text-muted text-xs">
              Page {page + 1} of {totalPages}
            </span>
            <div className="pagination-buttons">
              <button
                type="button"
                className="btn-pagination"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn-pagination"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
