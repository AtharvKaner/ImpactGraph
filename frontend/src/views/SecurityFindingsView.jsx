import React, { useState, useMemo, useEffect } from 'react';
import {
  IconSearch,
  IconClear,
  IconShieldAlert,
  IconCheckShield,
  IconAlertTriangle,
  IconCheck,
  IconSparkles,
  IconRefresh,
  IconCode,
} from '../components/Icons';
import {
  fetchSecurityFindings,
  verifyFinding,
  fetchAiExplanation,
} from '../services/impactService';

export function SecurityFindingsView({ onSelectServiceAndStudio, selectedProjectId }) {
  const [findings, setFindings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedFindingId, setSelectedFindingId] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [severityFilter, setSeverityFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Verification & AI states
  const [verifyingId, setVerifyingId] = useState(null);
  const [verificationFeedback, setVerificationFeedback] = useState(null);
  const [aiData, setAiData] = useState(null);
  const [loadingAi, setLoadingAi] = useState(false);

  const severities = ['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  const statuses = ['ALL', 'OPEN', 'IN_REVIEW', 'VERIFIED'];

  useEffect(() => {
    let ignore = false;
    async function loadData() {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchSecurityFindings({ projectId: selectedProjectId });
        if (!ignore) {
          setFindings(data);
          if (data.length > 0) {
            setSelectedFindingId((prev) => (prev ? prev : data[0].id));
          }
        }
      } catch (err) {
        if (!ignore) setError(err.message || 'Failed to load security findings');
      } finally {
        if (!ignore) setLoading(false);
      }
    }
    loadData();
    return () => {
      ignore = true;
    };
  }, [reloadKey, selectedProjectId]);

  const selectedFinding = useMemo(() => {
    return findings.find((f) => f.id === selectedFindingId) || findings[0] || null;
  }, [findings, selectedFindingId]);

  // Load AI explanation when selected finding changes
  useEffect(() => {
    if (!selectedFindingId) {
      return undefined;
    }
    let ignore = false;
    async function loadAi() {
      setLoadingAi(true);
      try {
        const res = await fetchAiExplanation(selectedFindingId);
        if (!ignore) setAiData(res);
      } catch {
        if (!ignore) setAiData(null);
      } finally {
        if (!ignore) setLoadingAi(false);
      }
    }
    loadAi();
    return () => {
      ignore = true;
    };
  }, [selectedFindingId]);

  const filteredFindings = useMemo(() => {
    return findings.filter((f) => {
      const matchesSearch = !searchTerm.trim() ||
        f.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        f.ruleId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (f.affectedComponent || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (f.affectedEndpoint || '').toLowerCase().includes(searchTerm.toLowerCase());
      const matchesSeverity = severityFilter === 'ALL' || f.severity === severityFilter;
      const matchesStatus = statusFilter === 'ALL' || f.status === statusFilter;
      return matchesSearch && matchesSeverity && matchesStatus;
    });
  }, [findings, searchTerm, severityFilter, statusFilter]);

  const handleVerify = async (findingId) => {
    setVerifyingId(findingId);
    setVerificationFeedback(null);
    try {
      const result = await verifyFinding(findingId);
      setVerificationFeedback(result);
      setReloadKey((k) => k + 1);
    } catch (err) {
      setVerificationFeedback({
        verifiedResolved: false,
        verificationMessage: err.message || 'Verification execution failed',
      });
    } finally {
      setVerifyingId(null);
    }
  };

  return (
    <div className="view-container security-findings-view">
      <div className="view-header-row">
        <div>
          <h2 className="view-title">Security & Architecture Findings Workspace</h2>
          <p className="view-subtitle">
            Deterministic rule violations, evidence propagation trails, targeted test recommendations, and verification checks
          </p>
        </div>
        <button type="button" className="btn-secondary" onClick={() => setReloadKey((k) => k + 1)} disabled={loading}>
          <IconRefresh size={14} className={loading ? 'spin-icon' : ''} />
          <span>Refresh Findings</span>
        </button>
      </div>

      {/* Control Bar: Filters & Search */}
      <div className="table-controls-bar">
        <div className="filter-search-box">
          <IconSearch size={14} className="filter-search-icon" />
          <input
            type="text"
            className="filter-search-input"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by rule (SEC-...), endpoint, or component..."
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
          <span className="filter-label">Severity:</span>
          <div className="segmented-toggle">
            {severities.map((s) => (
              <button
                key={s}
                type="button"
                className={`toggle-btn ${severityFilter === s ? 'active' : ''}`}
                onClick={() => setSeverityFilter(s)}
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        <div className="filter-dropdown-group">
          <span className="filter-label">Status:</span>
          <div className="segmented-toggle">
            {statuses.map((st) => (
              <button
                key={st}
                type="button"
                className={`toggle-btn ${statusFilter === st ? 'active' : ''}`}
                onClick={() => setStatusFilter(st)}
              >
                {st}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Master-Detail Split Workspace */}
      <div className="findings-workspace-grid">
        {/* Left List of Findings */}
        <div className="findings-master-column">
          {loading ? (
            <div className="table-loading-box">
              <span className="spinner" />
              <p>Scanning findings repository...</p>
            </div>
          ) : error ? (
            <div className="table-error-box">{error}</div>
          ) : (
            <div className="findings-cards-list">
              {filteredFindings.map((f) => {
                const isSelected = selectedFinding?.id === f.id;
                return (
                  <div
                    key={f.id}
                    className={`finding-card-item ${isSelected ? 'selected' : ''}`}
                    onClick={() => {
                      setSelectedFindingId(f.id);
                      setVerificationFeedback(null);
                    }}
                    role="button"
                    tabIndex={0}
                  >
                    <div className="finding-card-head">
                      <span className={`severity-tag tag-${f.severity.toLowerCase()}`}>
                        {f.severity}
                      </span>
                      <span className="finding-rule-pill">{f.ruleId}</span>
                      <span className={`status-pill pill-${f.status.toLowerCase()}`}>
                        {f.status}
                      </span>
                    </div>

                    <h4 className="finding-card-title">{f.title}</h4>

                    <div className="finding-card-meta">
                      <span>Component: <strong>{f.affectedComponent || 'Core'}</strong></span>
                      {f.affectedEndpoint && (
                        <span>Endpoint: <code>{f.affectedEndpoint}</code></span>
                      )}
                    </div>
                  </div>
                );
              })}

              {filteredFindings.length === 0 && (
                <div className="empty-catalog-state">
                  <IconCheckShield size={28} className="text-success" />
                  <h4>No findings match your filters</h4>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Right Detail Inspector Drawer */}
        <div className="findings-detail-column">
          {selectedFinding ? (
            <div className="finding-detail-card">
              <div className="finding-detail-header">
                <div className="header-badges-row">
                  <span className={`severity-tag tag-${selectedFinding.severity.toLowerCase()}`}>
                    {selectedFinding.severity}
                  </span>
                  <span className="finding-rule-pill">{selectedFinding.ruleId}</span>
                  <span className={`status-pill pill-${selectedFinding.status.toLowerCase()}`}>
                    {selectedFinding.status}
                  </span>
                </div>
                <h3 className="finding-detail-title">{selectedFinding.title}</h3>
                <p className="finding-detail-desc">{selectedFinding.description}</p>
              </div>

              {/* Verification Feedback Banner */}
              {verificationFeedback && (
                <div className={`verification-feedback-banner ${verificationFeedback.verifiedResolved ? 'banner-resolved' : 'banner-unresolved'}`}>
                  {verificationFeedback.verifiedResolved ? (
                    <IconCheck size={18} className="text-success" />
                  ) : (
                    <IconAlertTriangle size={18} className="text-warning" />
                  )}
                  <div className="feedback-text">
                    <strong>{verificationFeedback.verifiedResolved ? 'Verification Passed: Violation Resolved!' : 'Verification Result'}</strong>
                    <p>{verificationFeedback.verificationMessage}</p>
                    {verificationFeedback.executedCheck && (
                      <span className="check-name">Executed: {verificationFeedback.executedCheck}</span>
                    )}
                  </div>
                </div>
              )}

              {/* Evidence & Path Inspection */}
              <div className="detail-section-block">
                <h4 className="detail-section-heading">Evidence & Propagation Trail</h4>
                <div className="evidence-code-box">
                  <code>{selectedFinding.evidencePath || 'Direct rule match against Spring Boot source AST.'}</code>
                </div>
                {selectedFinding.filePath && (
                  <div className="file-location-row">
                    <span className="location-label">Source Location:</span>
                    <code className="location-code">
                      {selectedFinding.filePath}
                      {selectedFinding.startLine ? `:${selectedFinding.startLine}` : ''}
                      {selectedFinding.endLine ? `-${selectedFinding.endLine}` : ''}
                    </code>
                  </div>
                )}
              </div>

              {/* Recommended Regression Tests */}
              {selectedFinding.suggestedTests && (
                <div className="detail-section-block">
                  <h4 className="detail-section-heading">Recommended Targeted Regression Tests</h4>
                  <div className="tests-recommendation-box">
                    <IconCode size={16} className="text-primary" />
                    <code>{selectedFinding.suggestedTests}</code>
                  </div>
                </div>
              )}

              {/* Suggested Remediation */}
              {selectedFinding.suggestedRemediation && (
                <div className="detail-section-block">
                  <h4 className="detail-section-heading">Remediation Guidance</h4>
                  <p className="remediation-text">{selectedFinding.suggestedRemediation}</p>
                </div>
              )}

              {/* AI-Assisted Security Explanation */}
              <div className="detail-section-block ai-explanation-block">
                <div className="ai-block-head">
                  <div className="ai-title-wrap">
                    <IconSparkles size={16} className="text-purple" />
                    <h4 className="detail-section-heading">AI-Assisted Security Explanation & Code Patch</h4>
                  </div>
                  {aiData && (
                    <span className="ai-provider-tag">{aiData.modelProvider}</span>
                  )}
                </div>

                {loadingAi ? (
                  <div className="ai-loading-box">
                    <span className="spinner-inline" />
                    <span>Querying security knowledge engine...</span>
                  </div>
                ) : aiData ? (
                  <div className="ai-content-box">
                    <div className="ai-threat-summary">
                      <strong>Threat Scenario:</strong>
                      <p>{aiData.threatSummary}</p>
                    </div>

                    {aiData.remediationCodeSnippet && (
                      <div className="ai-code-patch-block">
                        <span className="code-patch-label">Suggested Code Patch:</span>
                        <pre className="remediation-pre">
                          <code>{aiData.remediationCodeSnippet}</code>
                        </pre>
                      </div>
                    )}

                    <div className="ai-limitations-caption">
                      <strong>Analysis Guarantee:</strong> {aiData.limitationsAndUncertainty}
                    </div>
                  </div>
                ) : (
                  <p className="text-muted">No additional AI insights available for this finding.</p>
                )}
              </div>

              {/* Action Buttons: Verify & Jump to Studio */}
              <div className="finding-actions-footer">
                <button
                  type="button"
                  className="btn-primary-action"
                  onClick={() => handleVerify(selectedFinding.id)}
                  disabled={verifyingId === selectedFinding.id}
                >
                  <IconCheck size={16} />
                  <span>
                    {verifyingId === selectedFinding.id ? 'Running Verification...' : 'Execute Deterministic Verification'}
                  </span>
                </button>

                {selectedFinding.affectedComponent && onSelectServiceAndStudio && (
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => onSelectServiceAndStudio(selectedFinding.affectedComponent)}
                  >
                    <span>View in Impact Studio &rarr;</span>
                  </button>
                )}
              </div>
            </div>
          ) : (
            <div className="empty-detail-placeholder">
              <IconShieldAlert size={36} className="text-muted" />
              <h4>Select a finding to inspect evidence and verify resolution</h4>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
