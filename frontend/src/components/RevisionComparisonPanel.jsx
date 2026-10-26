import React, { useState, useEffect } from 'react';
import {
  Icon,
  IconImpact,
  IconShieldAlert,
  IconCheckShield,
  IconAlertTriangle,
  IconCode,
  IconCheck,
} from './Icons.jsx';
import { compareRevisions, fetchWorkspaceRevisions } from '../services/impactService.js';
import './RevisionComparisonPanel.css';

export function RevisionComparisonPanel({
  selectedProjectId,
  projects = [],
  onApplyBlastRadiusToGraph,
  onSelectService,
}) {
  const [baseRevision, setBaseRevision] = useState('HEAD~1');
  const [targetRevision, setTargetRevision] = useState('HEAD');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [comparisonResult, setComparisonResult] = useState(null);
  const [activeTab, setActiveTab] = useState('diff'); // 'diff' | 'apis' | 'findings' | 'tests'
  const [availableRevisions, setAvailableRevisions] = useState([]);

  useEffect(() => {
    // Attempt to fetch recent Git commit log suggestions
    fetchWorkspaceRevisions(15)
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setAvailableRevisions(data);
        }
      })
      .catch(() => {});
  }, []);

  const handleCompare = async (e) => {
    if (e) e.preventDefault();
    if (!selectedProjectId) {
      setError('Please select or import a project first to compare revisions.');
      return;
    }
    setError(null);
    setLoading(true);

    try {
      const res = await compareRevisions({
        projectId: selectedProjectId,
        baseRevision: baseRevision.trim() || 'HEAD~1',
        targetRevision: targetRevision.trim() || 'HEAD',
      });
      setComparisonResult(res);

      // Notify parent to highlight blast radius on dependency graph
      if (onApplyBlastRadiusToGraph) {
        onApplyBlastRadiusToGraph(res);
      }
    } catch (err) {
      setError(err.message || 'Revision comparison failed.');
      setComparisonResult(null);
    } finally {
      setLoading(false);
    }
  };

  const currentProject = projects.find((p) => p.id === selectedProjectId);

  return (
    <div className="revision-panel-container">
      {/* Revision Selection Header */}
      <div className="revision-control-header">
        <div className="revision-control-title-row">
          <span className="revision-badge">Git Diff Engine</span>
          <h4>Revision Comparison &amp; Impact Analysis</h4>
          {currentProject && (
            <span className="chips-label">({currentProject.name})</span>
          )}
        </div>
        <p className="revision-control-subtitle">
          Compare Git commits/branches, map changed files to architectural services, and calculate downstream API and security blast radius.
        </p>

        <form onSubmit={handleCompare} className="revision-form">
          <div className="revision-inputs-row">
            <div className="rev-input-group">
              <label htmlFor="baseRev">Base Revision (Before)</label>
              <input
                id="baseRev"
                type="text"
                placeholder="HEAD~1, main, or commit SHA"
                value={baseRevision}
                onChange={(e) => setBaseRevision(e.target.value)}
                disabled={loading}
                className="rev-input"
                list="rev-suggestions-base"
              />
              <datalist id="rev-suggestions-base">
                <option value="HEAD~1" />
                <option value="HEAD~2" />
                <option value="main" />
                {availableRevisions.map((r) => (
                  <option key={r.sha} value={r.sha}>{r.summary}</option>
                ))}
              </datalist>
            </div>

            <span className="rev-arrow">&rarr;</span>

            <div className="rev-input-group">
              <label htmlFor="targetRev">Target Revision (After)</label>
              <input
                id="targetRev"
                type="text"
                placeholder="HEAD, feature-branch, or commit SHA"
                value={targetRevision}
                onChange={(e) => setTargetRevision(e.target.value)}
                disabled={loading}
                className="rev-input"
                list="rev-suggestions-target"
              />
              <datalist id="rev-suggestions-target">
                <option value="HEAD" />
                {availableRevisions.map((r) => (
                  <option key={r.sha} value={r.sha}>{r.summary}</option>
                ))}
              </datalist>
            </div>

            <button
              type="submit"
              className="btn-compare-revisions"
              disabled={loading || !selectedProjectId}
              title={!selectedProjectId ? 'Select a project first' : 'Run Revision Impact Analysis'}
            >
              {loading ? (
                <>
                  <Icon name="refresh" size={14} className="spin" />
                  <span>Comparing Diff...</span>
                </>
              ) : (
                <>
                  <IconImpact size={15} />
                  <span>Calculate Blast Radius</span>
                </>
              )}
            </button>
          </div>

          <div className="quick-rev-chips">
            <span className="chips-label">Presets:</span>
            <button
              type="button"
              className="rev-chip"
              onClick={() => { setBaseRevision('HEAD~1'); setTargetRevision('HEAD'); }}
            >
              HEAD~1 &rarr; HEAD
            </button>
            <button
              type="button"
              className="rev-chip"
              onClick={() => { setBaseRevision('HEAD~2'); setTargetRevision('HEAD'); }}
            >
              HEAD~2 &rarr; HEAD
            </button>
            {availableRevisions.length >= 2 && (
              <button
                type="button"
                className="rev-chip"
                onClick={() => {
                  setBaseRevision(availableRevisions[1].sha);
                  setTargetRevision(availableRevisions[0].sha);
                }}
              >
                Previous Commit &rarr; Latest
              </button>
            )}
          </div>
        </form>

        {error && (
          <div className="rev-error-alert" role="alert">
            <IconAlertTriangle size={16} />
            <span>{error}</span>
          </div>
        )}
      </div>

      {/* Comparison Results */}
      {comparisonResult ? (
        <div className="revision-results-wrapper">
          {/* Top KPI Metrics Row */}
          <div className="rev-metrics-grid">
            <div className="rev-metric-card">
              <span className="metric-val">{comparisonResult.changedFilesCount}</span>
              <span className="metric-title">Changed Files</span>
              <span className="metric-sub">{comparisonResult.baseRevision.slice(0, 7)}..{comparisonResult.targetRevision.slice(0, 7)}</span>
            </div>
            <div className="rev-metric-card highlight-amber">
              <span className="metric-val">{comparisonResult.directlyImpactedServices?.length || 0}</span>
              <span className="metric-title">Direct Services</span>
              <span className="metric-sub">Directly modified</span>
            </div>
            <div className="rev-metric-card highlight-purple">
              <span className="metric-val">{comparisonResult.transitivelyImpactedServices?.length || 0}</span>
              <span className="metric-title">Transitive Services</span>
              <span className="metric-sub">Upstream callers affected</span>
            </div>
            <div className="rev-metric-card highlight-blue">
              <span className="metric-val">{comparisonResult.impactedApis?.length || 0}</span>
              <span className="metric-title">Impacted APIs</span>
              <span className="metric-sub">Exposed routes</span>
            </div>
            <div className="rev-metric-card highlight-red">
              <span className="metric-val">{comparisonResult.securityFindingsInScope?.length || 0}</span>
              <span className="metric-title">Security Risks</span>
              <span className="metric-sub">In blast radius</span>
            </div>
          </div>

          {/* Impacted Components Summary Pill Row */}
          <div className="affected-services-row">
            <div className="affected-group">
              <span className="group-label">Directly Modified:</span>
              {(comparisonResult.directlyImpactedServices || []).length === 0 ? (
                <span className="empty-pill">No service files modified (global or non-service files)</span>
              ) : (
                comparisonResult.directlyImpactedServices.map((svc) => (
                  <button
                    key={svc}
                    type="button"
                    className="service-badge-pill direct"
                    onClick={() => onSelectService && onSelectService(svc)}
                    title="Inspect service in graph"
                  >
                    <span className="dot" />
                    <span>{svc}</span>
                  </button>
                ))
              )}
            </div>

            {(comparisonResult.transitivelyImpactedServices || []).length > 0 && (
              <div className="affected-group">
                <span className="group-label">Downstream Blast Radius:</span>
                {comparisonResult.transitivelyImpactedServices.map((svc) => (
                  <button
                    key={svc}
                    type="button"
                    className="service-badge-pill transitive"
                    onClick={() => onSelectService && onSelectService(svc)}
                    title="Inspect service in graph"
                  >
                    <span className="dot" />
                    <span>{svc}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Subtabs for detailed breakdowns */}
          <div className="rev-subtabs-bar">
            <button
              type="button"
              className={`rev-tab-btn ${activeTab === 'diff' ? 'active' : ''}`}
              onClick={() => setActiveTab('diff')}
            >
              <IconCode size={14} />
              <span>Changed Files ({comparisonResult.changedFiles?.length || 0})</span>
            </button>
            <button
              type="button"
              className={`rev-tab-btn ${activeTab === 'apis' ? 'active' : ''}`}
              onClick={() => setActiveTab('apis')}
            >
              <IconImpact size={14} />
              <span>Impacted APIs ({comparisonResult.impactedApis?.length || 0})</span>
            </button>
            <button
              type="button"
              className={`rev-tab-btn ${activeTab === 'findings' ? 'active' : ''}`}
              onClick={() => setActiveTab('findings')}
            >
              <IconShieldAlert size={14} />
              <span>Correlated Findings ({comparisonResult.securityFindingsInScope?.length || 0})</span>
            </button>
            <button
              type="button"
              className={`rev-tab-btn ${activeTab === 'tests' ? 'active' : ''}`}
              onClick={() => setActiveTab('tests')}
            >
              <IconCheckShield size={14} />
              <span>Targeted Tests ({comparisonResult.recommendedTests?.length || 0})</span>
            </button>
          </div>

          {/* Tab Content Panes */}
          <div className="rev-tab-content">
            {/* Tab 1: Changed Files with Mapped Services */}
            {activeTab === 'diff' && (
              <div className="tab-pane-diff">
                <div className="changed-files-list">
                  {(comparisonResult.changedFiles || []).map((file, idx) => (
                    <div key={idx} className="changed-file-row">
                      <div className="file-header">
                        <span className={`diff-badge badge-${(file.changeType || 'M').toLowerCase()}`}>
                          {file.changeType === 'ADDED' ? 'A' : file.changeType === 'DELETED' ? 'D' : 'M'}
                        </span>
                        <span className="file-path" title={file.filePath}>{file.filePath}</span>
                        {(file.linesAdded > 0 || file.linesDeleted > 0) && (
                          <span className="diff-lines-stat">
                            {file.linesAdded > 0 && <span className="diff-add">+{file.linesAdded}</span>}
                            {file.linesDeleted > 0 && <span className="diff-del">-{file.linesDeleted}</span>}
                          </span>
                        )}
                      </div>
                      <div className="file-mapping">
                        <span className="mapping-label">Mapped Component:</span>
                        {file.mappedService ? (
                          <span className="mapped-service-tag">{file.mappedService}</span>
                        ) : (
                          <span className="unmapped-tag">Global Config / Non-Service Asset</span>
                        )}
                      </div>
                    </div>
                  ))}
                  {(comparisonResult.changedFiles || []).length === 0 && (
                    <div className="empty-subtab">No file modifications between these revisions.</div>
                  )}
                </div>
              </div>
            )}

            {/* Tab 2: Impacted APIs */}
            {activeTab === 'apis' && (
              <div className="tab-pane-apis">
                <div className="apis-table-wrapper">
                  <table className="rev-apis-table">
                    <thead>
                      <tr>
                        <th>Method</th>
                        <th>Path</th>
                        <th>Component</th>
                        <th>Handler</th>
                        <th>Security</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(comparisonResult.impactedApis || []).map((api, idx) => (
                        <tr key={idx}>
                          <td>
                            <span className={`http-method method-${(api.method || 'GET').toLowerCase()}`}>
                              {api.method || 'GET'}
                            </span>
                          </td>
                          <td className="api-path-cell">
                            <code>{api.path || '/'}</code>
                          </td>
                          <td>
                            <span className="api-comp-name">{api.controllerName || api.component || 'Service'}</span>
                          </td>
                          <td className="api-handler-cell">
                            <code>{api.handlerMethod || '-'}</code>
                          </td>
                          <td>
                            <span className={`risk-badge risk-${(api.riskLevel || 'LOW').toLowerCase()}`}>
                              {api.riskLevel || 'STANDARD'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {(comparisonResult.impactedApis || []).length === 0 && (
                    <div className="empty-subtab">No public REST APIs directly impacted in blast radius.</div>
                  )}
                </div>
              </div>
            )}

            {/* Tab 3: Security Findings in Scope */}
            {activeTab === 'findings' && (
              <div className="tab-pane-findings">
                <div className="rev-findings-list">
                  {(comparisonResult.securityFindingsInScope || []).map((f) => (
                    <div key={f.id} className="rev-finding-card">
                      <div className="finding-header">
                        <span className={`severity-tag tag-${(f.severity || 'MEDIUM').toLowerCase()}`}>
                          {f.severity}
                        </span>
                        <strong className="finding-title">{f.title}</strong>
                        <span className="finding-rule">{f.ruleId}</span>
                      </div>
                      <p className="finding-desc">{f.description}</p>
                      <div className="finding-meta">
                        <span>Component: <strong>{f.affectedComponent}</strong></span>
                        {f.affectedEndpoint && <span>Endpoint: <code>{f.affectedEndpoint}</code></span>}
                        {f.startLine && <span>Line: {f.startLine}</span>}
                      </div>
                    </div>
                  ))}
                  {(comparisonResult.securityFindingsInScope || []).length === 0 && (
                    <div className="empty-subtab">
                      <IconCheck size={20} className="text-success" />
                      <span>No active security findings in the changed components blast radius.</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Tab 4: Targeted Regression Tests */}
            {activeTab === 'tests' && (
              <div className="tab-pane-tests">
                <div className="tests-list">
                  {(comparisonResult.recommendedTests || []).map((test, idx) => (
                    <div key={idx} className="test-recommendation-item">
                      <div className="test-icon-badge">
                        <IconCheckShield size={16} />
                      </div>
                      <div className="test-body">
                        <code className="test-class-name">{test}</code>
                        <span className="test-reason">
                          Recommended based on static AST changes to callers and endpoints in the affected services.
                        </span>
                      </div>
                    </div>
                  ))}
                  {(comparisonResult.recommendedTests || []).length === 0 && (
                    <div className="empty-subtab">No specific targeted test suites required.</div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Analysis Transparency Banner */}
          <div className="confidence-banner">
            <div className="confidence-left">
              <span className="conf-badge">Evidence: {comparisonResult.confidence || 'STATIC_EVIDENCE'}</span>
              <p className="conf-text">
                <strong>Static AST &amp; Git Diff Analysis:</strong> {comparisonResult.limitations || 'Static file inspection and dependency resolution. Dynamic RPC calls, runtime reflection, or indirect message queues without static bindings require runtime tracing.'}
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="revision-empty-state">
          <div className="empty-icon-wrap">
            <IconCode size={32} />
          </div>
          <h5>No Revision Comparison Executed Yet</h5>
          <p>
            Select a base revision (e.g. <code>HEAD~1</code>) and target revision (e.g. <code>HEAD</code>) above and click <strong>Calculate Blast Radius</strong> to analyze code changes.
          </p>
        </div>
      )}
    </div>
  );
}
