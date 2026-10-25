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
  apiBaseUrl,
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
                <strong>{healthData ? 'Backend Service Online' : 'Backend Unreachable'}</strong>
                <span>
                  {loading
                    ? 'Pinging backend health endpoint...'
                    : healthData
                    ? 'Connected to Spring Boot on port 8080 (UP)'
                    : 'Unable to reach backend health endpoint'}
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
              <span className="detail-key">Health Endpoint</span>
              <code className="detail-code">{(apiBaseUrl || 'http://localhost:8080').replace(/\/+$/, '')}/api/health</code>
            </div>
            <div className="detail-item">
              <span className="detail-key">Data Source</span>
              <span className="detail-val">
                {healthData ? 'Live Backend REST API' : 'Offline Demonstration Fallback'}
              </span>
            </div>
            <div className="detail-item">
              <span className="detail-key">Backend Service</span>
              <span className="detail-val">{healthData?.service || 'ImpactGraph Backend'}</span>
            </div>
            <div className="detail-item">
              <span className="detail-key">Reported Health</span>
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

          {/* Architecture / Integration Notice */}
          <div className="modal-notice-box">
            <strong>Architecture & Persistence Notice:</strong>
            <p>
              Phase 2A features in-memory graph models mirroring backend Java domain records. Live PostgreSQL database persistence integration will connect in Phase 2B.
            </p>
          </div>

          {error && (
            <div className="modal-error-box">
              <strong>Connection Troubleshooting:</strong>
              <p>{error}</p>
              <p className="error-tip">
                To start the backend server, run in <code>./backend</code>:
                <br />
                <code>.\mvnw.cmd spring-boot:run</code>
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
