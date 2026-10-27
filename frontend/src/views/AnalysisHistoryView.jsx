import React, { useState, useEffect, useMemo } from 'react';
import {
  IconHistory,
  IconRefresh,
  IconSearch,
  IconShieldAlert,
  IconCheckShield,
  IconChevronRight,
} from '../components/Icons';
import { fetchAnalysisHistory, analyzeSecurityDiff } from '../services/impactService';

export function AnalysisHistoryView({ onNavigate, selectedProjectId }) {
  const [historyRuns, setHistoryRuns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedRunId, setSelectedRunId] = useState(null);

  // Analysis trigger modal / state
  const [analyzing, setAnalyzing] = useState(false);
  const [triggerFeedback, setTriggerFeedback] = useState(null);
  const [baseRev, setBaseRev] = useState('develop');
  const [targetRev, setTargetRev] = useState('HEAD');
  const [sampleProject, setSampleProject] = useState('e-commerce-backend');

  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState('ALL');

  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    async function loadData() {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchAnalysisHistory(selectedProjectId);
        if (!ignore) {
          setHistoryRuns(data);
          if (data.length > 0 && !selectedRunId) {
            setSelectedRunId(data[0].id);
          }
        }
      } catch (err) {
        if (!ignore) setError(err.message || 'Failed to load analysis history');
      } finally {
        if (!ignore) setLoading(false);
      }
    }
    loadData();
    return () => {
      ignore = true;
    };
  }, [reloadKey, selectedRunId, selectedProjectId]);

  const selectedRun = useMemo(() => {
    return historyRuns.find((r) => r.id === selectedRunId) || historyRuns[0] || null;
  }, [historyRuns, selectedRunId]);

  const filteredRuns = useMemo(() => {
    return historyRuns.filter((r) => {
      const matchesSearch =
        !searchTerm.trim() ||
        (r.runType || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (r.commitSha || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (r.summary || '').toLowerCase().includes(searchTerm.toLowerCase());
      const matchesType = filterType === 'ALL' || r.runType === filterType;
      return matchesSearch && matchesType;
    });
  }, [historyRuns, searchTerm, filterType]);

  const handleTriggerAnalysis = async (e) => {
    e.preventDefault();
    setAnalyzing(true);
    setTriggerFeedback(null);
    try {
      const res = await analyzeSecurityDiff({
        baseRevision: baseRev,
        targetRevision: targetRev,
        projectName: sampleProject,
      });
      setTriggerFeedback({
        success: true,
        message: `Analysis completed! Generated Run ID: ${res.runId} with ${res.findingsCount} findings.`,
      });
      setReloadKey((k) => k + 1);
      setSelectedRunId(res.runId);
    } catch (err) {
      setTriggerFeedback({
        success: false,
        message: err.message || 'Analysis failed to execute.',
      });
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="view-container analysis-history-view">
      {/* Header */}
      <div className="view-header-row">
        <div>
          <h2 className="view-title">Analysis History & Auditable Run Ledger</h2>
          <p className="view-subtitle">
            Durable records of all deterministic security blast-radius scans, PR webhook check runs, and component impact evaluations.
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
            <span>Refresh Ledger</span>
          </button>
        </div>
      </div>

      {/* Trigger Form Panel */}
      <div className="card-panel trigger-run-panel" style={{ marginBottom: '1.5rem' }}>
        <div className="panel-header">
          <div className="panel-title-group">
            <span className="panel-title">Execute Revision Change-Impact Analysis</span>
            <span className="badge badge-info">Deterministic Spring AST Engine</span>
          </div>
        </div>
        <form onSubmit={handleTriggerAnalysis} className="trigger-form-grid">
          <div className="form-group">
            <label className="form-label">Base Git Revision</label>
            <input
              type="text"
              className="text-input"
              value={baseRev}
              onChange={(e) => setBaseRev(e.target.value)}
              placeholder="e.g. develop or main"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Target / Proposed Revision</label>
            <input
              type="text"
              className="text-input"
              value={targetRev}
              onChange={(e) => setTargetRev(e.target.value)}
              placeholder="e.g. feat/dashboard-redesign or HEAD"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Target Repository / Module</label>
            <select
              className="select-input"
              value={sampleProject}
              onChange={(e) => setSampleProject(e.target.value)}
            >
              <option value="e-commerce-backend">e-commerce-backend (Live AST)</option>
              <option value="auth-service-monorepo">auth-service-monorepo</option>
              <option value="payment-gateway">payment-gateway</option>
            </select>
          </div>
          <div className="form-actions-inline" style={{ alignSelf: 'flex-end' }}>
            <button
              type="submit"
              className="btn-primary-action"
              disabled={analyzing}
              style={{ minWidth: '160px' }}
            >
              <IconHistory size={15} />
              <span>{analyzing ? 'Analyzing AST...' : 'Run Analysis'}</span>
            </button>
          </div>
        </form>

        {triggerFeedback && (
          <div
            className={`feedback-banner ${triggerFeedback.success ? 'feedback-success' : 'feedback-error'}`}
            style={{ marginTop: '1rem' }}
          >
            {triggerFeedback.success ? <IconCheckShield size={16} /> : <IconShieldAlert size={16} />}
            <span>{triggerFeedback.message}</span>
          </div>
        )}
      </div>

      {/* Main Split: Runs Table + Run Inspector */}
      <div className="two-column-workspace">
        {/* Left Column: History Ledger */}
        <div className="column-main card-panel">
          <div className="panel-header">
            <div className="panel-title-group">
              <span className="panel-title">Recorded Executions</span>
              <span className="badge badge-neutral">{filteredRuns.length} runs</span>
            </div>
            <div className="panel-filter-row">
              <div className="search-bar-inline">
                <IconSearch size={14} className="search-icon" />
                <input
                  type="text"
                  placeholder="Filter by SHA, type, summary..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
              <select
                className="select-filter-compact"
                value={filterType}
                onChange={(e) => setFilterType(e.target.value)}
              >
                <option value="ALL">All Types</option>
                <option value="SIMULATION">SIMULATION</option>
                <option value="COMMIT_DIFF">COMMIT_DIFF</option>
                <option value="PR_SCAN">PR_SCAN</option>
              </select>
            </div>
          </div>

          {loading ? (
            <div className="panel-empty-state">
              <div className="spinner-inline" />
              <span>Loading analysis ledger...</span>
            </div>
          ) : error ? (
            <div className="panel-empty-state text-danger">
              <IconShieldAlert size={28} />
              <p>{error}</p>
            </div>
          ) : filteredRuns.length === 0 ? (
            <div className="panel-empty-state">
              <IconHistory size={28} />
              <p>No historical runs found matching criteria.</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Run ID / Type</th>
                    <th>Revision & Base</th>
                    <th>Affected</th>
                    <th>Findings</th>
                    <th>Status</th>
                    <th>Timestamp</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRuns.map((run) => {
                    const isSelected = run.id === selectedRun?.id;
                    return (
                      <tr
                        key={run.id}
                        className={`clickable-row ${isSelected ? 'row-selected' : ''}`}
                        onClick={() => setSelectedRunId(run.id)}
                      >
                        <td>
                          <div className="cell-flex">
                            <span className="code-badge">{run.id.slice(0, 8)}</span>
                            <span className={`badge ${run.runType === 'PR_SCAN' ? 'badge-purple' : run.runType === 'COMMIT_DIFF' ? 'badge-blue' : 'badge-neutral'}`}>
                              {run.runType}
                            </span>
                          </div>
                        </td>
                        <td>
                          <div className="cell-subtext-group">
                            <span className="mono-text">{run.commitSha ? run.commitSha.slice(0, 7) : 'HEAD'}</span>
                            <span className="text-muted text-xs">base: {run.baseRevision || 'develop'}</span>
                          </div>
                        </td>
                        <td>
                          <span className="metric-val-bold">{run.affectedComponentsCount ?? 0}</span>
                          <span className="text-muted text-xs"> / {run.totalComponentsCount ?? 0} comps</span>
                        </td>
                        <td>
                          <span className={`status-pill ${(run.totalFindings ?? run.findingsCount ?? 0) > 0 ? 'status-failed' : 'status-clean'}`}>
                            {(run.totalFindings ?? run.findingsCount ?? 0) > 0 ? `${run.totalFindings ?? run.findingsCount} Issues` : 'Clean'}
                          </span>
                        </td>
                        <td>
                          <span className="badge badge-success">{run.status || 'COMPLETED'}</span>
                        </td>
                        <td>
                          <span className="text-muted text-xs">
                            {run.timestamp || run.createdAt ? new Date(run.timestamp || run.createdAt).toLocaleString() : 'Recent'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Right Column: Run Detail Inspector */}
        <div className="column-side card-panel">
          {selectedRun ? (
            <div className="inspector-content">
              <div className="inspector-header">
                <div>
                  <span className="badge badge-info">{selectedRun.runType}</span>
                  <h3 className="inspector-title" style={{ marginTop: '0.4rem' }}>
                    Run #{selectedRun.id.slice(0, 8)}
                  </h3>
                  <span className="text-muted text-xs">
                    Executed: {selectedRun.timestamp || selectedRun.createdAt ? new Date(selectedRun.timestamp || selectedRun.createdAt).toLocaleString() : 'N/A'}
                  </span>
                </div>
              </div>

              <div className="inspector-metrics-grid">
                <div className="inspector-metric-card">
                  <span className="inspector-metric-label">Findings</span>
                  <span className={`inspector-metric-value ${(selectedRun.totalFindings ?? selectedRun.findingsCount ?? 0) > 0 ? 'text-danger' : 'text-success'}`}>
                    {selectedRun.totalFindings ?? selectedRun.findingsCount ?? 0}
                  </span>
                </div>
                <div className="inspector-metric-card">
                  <span className="inspector-metric-label">Affected Nodes</span>
                  <span className="inspector-metric-value text-accent">
                    {selectedRun.affectedComponentsCount ?? 0}
                  </span>
                </div>
                <div className="inspector-metric-card">
                  <span className="inspector-metric-label">Max Depth</span>
                  <span className="inspector-metric-value">
                    {selectedRun.propagationDepth ?? 3} hops
                  </span>
                </div>
              </div>

              <div className="inspector-section">
                <h4 className="inspector-section-title">Execution Context & Summary</h4>
                <p className="inspector-body-text">{selectedRun.summary || 'Deterministic static AST analysis completed without regressions.'}</p>
                <div className="meta-list" style={{ marginTop: '0.75rem' }}>
                  <div className="meta-row">
                    <span className="meta-key">Triggered By</span>
                    <span className="meta-value">{selectedRun.triggeredBy || 'Manual Dashboard Scan'}</span>
                  </div>
                  <div className="meta-row">
                    <span className="meta-key">Target Revision</span>
                    <span className="meta-value mono-text">{selectedRun.commitSha || 'HEAD'}</span>
                  </div>
                  <div className="meta-row">
                    <span className="meta-key">Base Revision</span>
                    <span className="meta-value mono-text">{selectedRun.baseRevision || 'develop'}</span>
                  </div>
                </div>
              </div>

              {selectedRun.findingsCount > 0 && (
                <div className="inspector-section">
                  <h4 className="inspector-section-title">Associated Security Findings</h4>
                  <p className="text-muted text-xs">
                    This run discovered {selectedRun.findingsCount} security or architectural drifts.
                  </p>
                  <button
                    type="button"
                    className="btn-secondary-action full-width"
                    style={{ marginTop: '0.75rem' }}
                    onClick={() => onNavigate('findings')}
                  >
                    <IconShieldAlert size={14} />
                    <span>View Security Findings ({selectedRun.findingsCount})</span>
                  </button>
                </div>
              )}

              <div className="inspector-actions" style={{ marginTop: '1.5rem' }}>
                <button
                  type="button"
                  className="btn-primary-action full-width"
                  onClick={() => onNavigate('studio')}
                >
                  <IconChevronRight size={14} />
                  <span>Inspect in Studio Graph</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="panel-empty-state">
              <IconHistory size={32} />
              <p>Select a run from the ledger to inspect detailed execution metadata.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
