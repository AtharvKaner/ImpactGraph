import React from 'react';
import {
  IconSettings,
  IconRefresh,
  IconClear,
  IconCheckCircle,
  IconAlertTriangle,
} from './Icons';

export function BackendStatusModal({
  isOpen,
  onClose,
  healthData,
  loading,
  error,
  onRefresh,
  lastChecked,
  _apiBaseUrl,
}) {
  if (!isOpen) return null;

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-window" onClick={(e) => e.stopPropagation()}>
        {/* Modal Header */}
        <div className="modal-header">
          <div className="modal-title-wrap">
            <div className="modal-title-icon">
              <IconSettings size={18} className="text-primary" />
            </div>
            <div>
              <h3 className="modal-heading">Backend & System Status</h3>
              <p className="modal-subheading">Live connection monitor to Spring Boot backend</p>
            </div>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close modal"
          >
            <IconClear size={16} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="modal-body">
          {/* Status Banner */}
          <div className={`modal-status-pill-card ${healthData ? 'status-ok' : 'status-err'}`}>
            <div className="status-pill-left">
              {healthData ? (
                <IconCheckCircle size={20} className="icon-safe" />
              ) : (
                <IconAlertTriangle size={20} className="icon-alert" />
              )}
              <div className="status-pill-text">
                <strong>{healthData ? 'Analysis Engine Online' : 'Analysis Engine Offline'}</strong>
                <span>
                  {loading
                    ? 'Checking analysis service connectivity...'
                    : healthData
                    ? 'Connected to ImpactGraph Analysis Engine (Operational)'
                    : 'Unable to connect to analysis engine service'}
                </span>
              </div>
            </div>
            <button
              type="button"
              className={`modal-refresh-btn ${loading ? 'refreshing' : ''}`}
              onClick={onRefresh}
              disabled={loading}
            >
              <IconRefresh size={14} />
              <span>{loading ? 'Checking...' : 'Check Again'}</span>
            </button>
          </div>

          {/* Details Table */}
          <div className="modal-details-table">
            <div className="detail-item">
              <span className="detail-key">Service Endpoint</span>
              <code className="detail-code">/api/health</code>
            </div>
            <div className="detail-item">
              <span className="detail-key">Analysis Mode</span>
              <span className="detail-val">
                {healthData ? 'Live Repository & Static AST Engine' : 'Offline Mode'}
              </span>
            </div>
            <div className="detail-item">
              <span className="detail-key">System Engine</span>
              <span className="detail-val">{healthData?.service || 'ImpactGraph Analysis Platform'}</span>
            </div>
            <div className="detail-item">
              <span className="detail-key">Service Status</span>
              <span className={`detail-status-badge ${healthData ? 'badge-ok' : 'badge-err'}`}>
                {healthData?.status || (loading ? 'Checking...' : 'OFFLINE')}
              </span>
            </div>
            <div className="detail-item">
              <span className="detail-key">Server Timestamp</span>
              <span className="detail-val">{healthData?.timestamp || 'Unavailable'}</span>
            </div>
            <div className="detail-item">
              <span className="detail-key">Last Evaluated</span>
              <span className="detail-val">{lastChecked || 'Initial startup check'}</span>
            </div>
          </div>

          {/* System Capability Overview */}
          <div className="modal-notice-box">
            <strong>System Engine Capabilities:</strong>
            <p>
              Static AST parser, Git revision diff engine, directed dependency graph traversal, API endpoint correlation, and deterministic security rule evaluation.
            </p>
          </div>

          {error && (
            <div className="modal-error-box">
              <strong>Connection Notice:</strong>
              <p>{error}</p>
              <p className="error-tip">
                Ensure the ImpactGraph analysis service is active and listening on the designated network address.
              </p>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="modal-footer">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
