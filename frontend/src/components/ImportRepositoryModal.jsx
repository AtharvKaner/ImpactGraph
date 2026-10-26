import React, { useState, useEffect } from 'react';
import {
  importRepository,
  fetchLocalWorkspaceInfo,
} from '../services/impactService.js';
import { Icon } from './Icons.jsx';
import './ImportRepositoryModal.css';

export default function ImportRepositoryModal({ isOpen, onClose, onProjectImported }) {
  const [tab, setTab] = useState('github'); // 'github' | 'local'
  const [repoUrl, setRepoUrl] = useState('');
  const [branch, setBranch] = useState('main');
  const [token, setToken] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [projectName, setProjectName] = useState('');
  const [localPath, setLocalPath] = useState('');
  const [localWorkspace, setLocalWorkspace] = useState(null);

  const [loading, setLoading] = useState(false);
  const [progressStep, setProgressStep] = useState(0);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const handleClose = () => {
    setError(null);
    setResult(null);
    setLoading(false);
    setProgressStep(0);
    onClose();
  };

  useEffect(() => {
    if (!isOpen) return;
    fetchLocalWorkspaceInfo()
      .then((data) => {
        if (data && data.path) {
          setLocalWorkspace(data);
          setLocalPath((prev) => prev || data.path);
        }
      })
      .catch(() => {});
  }, [isOpen]);

  if (!isOpen) return null;

  const handleQuickUrl = (url) => {
    setRepoUrl(url);
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    setProgressStep(1);

    const stepTimer = setInterval(() => {
      setProgressStep((prev) => (prev < 3 ? prev + 1 : prev));
    }, 1200);

    try {
      const payload = {
        sourceType: tab === 'github' ? 'GITHUB' : 'LOCAL',
        repositoryUrl: tab === 'github' ? repoUrl.trim() : null,
        localPath: tab === 'local' ? localPath.trim() : null,
        branch: branch.trim() || 'main',
        personalAccessToken: token.trim() || null,
        projectName: projectName.trim() || null,
      };

      const res = await importRepository(payload);
      clearInterval(stepTimer);
      setProgressStep(4);
      setResult(res);
      if (onProjectImported) {
        onProjectImported(res);
      }
    } catch (err) {
      clearInterval(stepTimer);
      setError(err.message || 'Repository import failed. Please verify the URL and try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenStudio = () => {
    if (result && onProjectImported) {
      onProjectImported(result, true); // true to navigate to Impact Studio
    }
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={handleClose}>
      <div className="modal-dialog import-repo-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="modal-title-row">
            <span className="modal-icon"><Icon name="cloud" size={20} /></span>
            <div>
              <h3>Import Repository for Architecture Analysis</h3>
              <p className="modal-subtitle">Analyze dependencies, APIs, and change-impact blast radius on real code.</p>
            </div>
          </div>
          <button className="modal-close-btn" onClick={handleClose} aria-label="Close modal">
            <Icon name="close" size={18} />
          </button>
        </div>

        {!result ? (
          <form onSubmit={handleSubmit} className="modal-form">
            <div className="import-tabs">
              <button
                type="button"
                className={`import-tab-btn ${tab === 'github' ? 'active' : ''}`}
                onClick={() => { setTab('github'); setError(null); }}
              >
                <Icon name="globe" size={16} /> GitHub Repository
              </button>
              <button
                type="button"
                className={`import-tab-btn ${tab === 'local' ? 'active' : ''}`}
                onClick={() => { setTab('local'); setError(null); }}
              >
                <Icon name="layers" size={16} /> Local Workspace
              </button>
            </div>

            {tab === 'github' ? (
              <div className="tab-pane">
                <div className="form-group">
                  <label htmlFor="repoUrl">GitHub Repository URL <span className="required">*</span></label>
                  <input
                    id="repoUrl"
                    type="url"
                    placeholder="https://github.com/owner/repository"
                    value={repoUrl}
                    onChange={(e) => setRepoUrl(e.target.value)}
                    required
                    disabled={loading}
                    className="form-input"
                  />
                  <div className="quick-suggestions">
                    <span className="suggestions-label">Try fixture:</span>
                    <button
                      type="button"
                      className="suggestion-chip"
                      onClick={() => handleQuickUrl('https://github.com/AtharvKaner/ImpactGraph')}
                    >
                      AtharvKaner/ImpactGraph
                    </button>
                    <button
                      type="button"
                      className="suggestion-chip"
                      onClick={() => handleQuickUrl('https://github.com/spring-projects/spring-petclinic')}
                    >
                      spring-projects/spring-petclinic
                    </button>
                  </div>
                </div>

                <div className="form-row-2">
                  <div className="form-group">
                    <label htmlFor="branch">Branch or Revision</label>
                    <input
                      id="branch"
                      type="text"
                      placeholder="main"
                      value={branch}
                      onChange={(e) => setBranch(e.target.value)}
                      disabled={loading}
                      className="form-input"
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="projectName">Custom Project Name (Optional)</label>
                    <input
                      id="projectName"
                      type="text"
                      placeholder="e.g. Production Billing System"
                      value={projectName}
                      onChange={(e) => setProjectName(e.target.value)}
                      disabled={loading}
                      className="form-input"
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label htmlFor="token">
                    Personal Access Token (Private Repositories)
                    <span className="help-badge">Optional</span>
                  </label>
                  <div className="password-input-wrapper">
                    <input
                      id="token"
                      type={showToken ? 'text' : 'password'}
                      placeholder="ghp_xxxxxxxxxxxxxxxxxxxx (Never logged or persisted in plain text)"
                      value={token}
                      onChange={(e) => setToken(e.target.value)}
                      disabled={loading}
                      className="form-input"
                    />
                    <button
                      type="button"
                      className="token-toggle-btn"
                      onClick={() => setShowToken(!showToken)}
                    >
                      <Icon name={showToken ? 'eye-off' : 'eye'} size={16} />
                    </button>
                  </div>
                  <p className="field-hint">
                    <Icon name="shield" size={13} /> Only required for private repositories. Sent in-memory via extra HTTP header.
                  </p>
                </div>
              </div>
            ) : (
              <div className="tab-pane">
                <div className="form-group">
                  <label htmlFor="localPath">Local Directory Path <span className="required">*</span></label>
                  <input
                    id="localPath"
                    type="text"
                    placeholder="c:\path\to\repository"
                    value={localPath}
                    onChange={(e) => setLocalPath(e.target.value)}
                    required
                    disabled={loading}
                    className="form-input"
                  />
                </div>

                {localWorkspace && (
                  <div className="local-workspace-banner">
                    <div className="workspace-badge"><Icon name="check-circle" size={14} /> Active Workspace Detected</div>
                    <div className="workspace-details">
                      <strong>{localWorkspace.name}</strong> ({localWorkspace.branch})
                      <span className="workspace-path">{localWorkspace.path}</span>
                    </div>
                    <button
                      type="button"
                      className="btn-use-workspace"
                      onClick={() => setLocalPath(localWorkspace.path)}
                    >
                      Use Workspace
                    </button>
                  </div>
                )}

                <div className="form-group">
                  <label htmlFor="projectNameLocal">Project Name (Optional)</label>
                  <input
                    id="projectNameLocal"
                    type="text"
                    placeholder="e.g. Local ImpactGraph"
                    value={projectName}
                    onChange={(e) => setProjectName(e.target.value)}
                    disabled={loading}
                    className="form-input"
                  />
                </div>

                <p className="security-notice">
                  <Icon name="lock" size={14} />
                  Safe Static Inspection: ImpactGraph analyzes code structure without executing build scripts, maven wrappers, or arbitrary code.
                </p>
              </div>
            )}

            {error && (
              <div className="modal-error-alert" role="alert">
                <Icon name="alert-triangle" size={18} />
                <div className="error-text">
                  <strong>Analysis Error:</strong> {error}
                </div>
              </div>
            )}

            {loading && (
              <div className="analysis-progress-panel">
                <div className="progress-spinner">
                  <div className="spinner-glow"></div>
                </div>
                <div className="progress-steps">
                  <div className={`step-item ${progressStep >= 1 ? 'active' : ''}`}>
                    <Icon name={progressStep > 1 ? 'check-circle' : 'refresh'} size={14} />
                    <span>Cloning & Verifying Repository...</span>
                  </div>
                  <div className={`step-item ${progressStep >= 2 ? 'active' : ''}`}>
                    <Icon name={progressStep > 2 ? 'check-circle' : 'refresh'} size={14} />
                    <span>Extracting Modules, Controllers & Services...</span>
                  </div>
                  <div className={`step-item ${progressStep >= 3 ? 'active' : ''}`}>
                    <Icon name={progressStep > 3 ? 'check-circle' : 'refresh'} size={14} />
                    <span>Resolving Injections, Dependencies & APIs...</span>
                  </div>
                  <div className={`step-item ${progressStep >= 4 ? 'active' : ''}`}>
                    <Icon name={progressStep >= 4 ? 'check-circle' : 'refresh'} size={14} />
                    <span>Evaluating Security & Blast-Radius Engine...</span>
                  </div>
                </div>
              </div>
            )}

            <div className="modal-actions">
              <button type="button" className="btn-cancel" onClick={handleClose} disabled={loading}>
                Cancel
              </button>
              <button type="submit" className="btn-import-submit" disabled={loading}>
                {loading ? 'Analyzing Architecture...' : '🚀 Start Architecture Analysis'}
              </button>
            </div>
          </form>
        ) : (
          <div className="modal-success-screen">
            <div className="success-header">
              <div className="success-icon-badge">
                <Icon name="check-circle" size={32} />
              </div>
              <h4>Architecture Successfully Analyzed!</h4>
              <p className="success-summary">
                Imported <strong>{result.projectName}</strong> ({result.sourceType}) at commit <code>{result.commitSha?.slice(0, 8)}</code>.
              </p>
            </div>

            <div className="success-metrics-grid">
              <div className="metric-box">
                <span className="metric-num">{result.serviceCount}</span>
                <span className="metric-label">Services / Modules</span>
              </div>
              <div className="metric-box">
                <span className="metric-num">{result.dependencyCount}</span>
                <span className="metric-label">Dependency Edges</span>
              </div>
              <div className="metric-box">
                <span className="metric-num">{result.apiCount}</span>
                <span className="metric-label">Exposed APIs</span>
              </div>
              <div className="metric-box highlight">
                <span className="metric-num">{result.securityFindingCount}</span>
                <span className="metric-label">Security Findings</span>
              </div>
            </div>

            <div className="languages-detected">
              <span className="lang-label">Detected Stacks:</span>
              {(result.primaryLanguages || []).map((lang) => (
                <span key={lang} className="lang-tag">{lang}</span>
              ))}
            </div>

            <p className="confidence-note">
              <Icon name="info" size={14} />
              <strong>Analysis Confidence:</strong> {result.confidence} ({result.limitations})
            </p>

            <div className="modal-actions">
              <button type="button" className="btn-cancel" onClick={handleClose}>
                Done
              </button>
              <button type="button" className="btn-open-studio" onClick={handleOpenStudio}>
                Explore in Impact Studio →
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
