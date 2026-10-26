import React from 'react';
import {
  IconImpact,
  IconShieldAlert,
  IconCheckShield,
  IconAlertTriangle,
  IconServices,
  IconTopology,
  IconHistory,
  IconCode,
  IconChevronRight,
  ServiceIcon,
} from '../components/Icons';

export function OverviewView({
  services = [],
  dependencies = [],
  graphData,
  findings = [],
  historyRuns = [],
  auditEvents = [],
  user,
  onNavigate,
  onSelectServiceAndStudio,
  selectedProjectId,
  currentPreset,
  onOpenDemo,
  isDemoProject = false,
  onOpenImportModal = null,
}) {
  const isCircular = Boolean(graphData?.hasCycle);
  const criticalFindings = findings.filter((f) => f.severity === 'CRITICAL');
  const highFindings = findings.filter((f) => f.severity === 'HIGH');
  const mediumFindings = findings.filter((f) => f.severity === 'MEDIUM');
  const openFindings = findings.filter((f) => f.status === 'OPEN' || f.status === 'IN_REVIEW');

  const projectName = graphData?.name || (selectedProjectId ? `Project #${selectedProjectId}` : currentPreset);
  const isCurrentlyDemo = isDemoProject || (projectName && projectName.includes('[Demo]'));

  return (
    <div className="view-container overview-view">
      {/* Welcome & Context Header */}
      <div className="view-header-row">
        <div>
          <h2 className="view-title">Platform Overview & Architecture Posture</h2>
          <p className="view-subtitle">
            Welcome back, <strong>{user?.displayName || user?.email || 'Engineer'}</strong> ({user?.role || 'DEVELOPER'}).
            Software change impact analysis, dependency graph topology, and security drift telemetry for{' '}
            <strong>{projectName}</strong>
          </p>
        </div>
        <div className="view-header-actions">
          {onOpenImportModal && (
            <button
              type="button"
              className="btn-import-repo-header"
              onClick={onOpenImportModal}
              title="Import a real GitHub or local Git repository"
            >
              <span>📦 Import Repository</span>
            </button>
          )}
          {!isCurrentlyDemo && onOpenDemo && (
            <button
              type="button"
              className="btn-demo-explore-secondary"
              onClick={onOpenDemo}
              title="Open ready-to-explore multi-service demo project"
            >
              <span>⚡ Open Demo Project</span>
            </button>
          )}
          <button
            type="button"
            className="btn-primary-action"
            onClick={() => onNavigate('studio')}
          >
            <IconImpact size={16} />
            <span>Launch Impact Studio</span>
          </button>
        </div>
      </div>

      {/* Prominent Demo Experience Banner */}
      {isCurrentlyDemo ? (
        <div className="overview-demo-highlight-banner">
          <div className="demo-highlight-left">
            <span className="demo-highlight-badge">⚡ Interactive Demo Project Active</span>
            <h3 className="demo-highlight-title">[Demo] E-Commerce Multi-Service Platform</h3>
            <p className="demo-highlight-desc">
              Deterministic 4-tier microservice architecture with 8 services, 10 dependencies, and 5 security findings.
              Simulate a change on <strong>Payment Service</strong> to evaluate reverse reachability, blast radius, and vulnerability amplification.
            </p>
          </div>
          <div className="demo-highlight-actions">
            <button
              type="button"
              className="btn-demo-simulate-primary"
              onClick={() => onSelectServiceAndStudio('Payment Service')}
              title="Select Payment Service and jump into Impact Studio"
            >
              <IconImpact size={16} />
              <span>Simulate Payment Service Blast Radius &rarr;</span>
            </button>
            <button
              type="button"
              className="btn-demo-explore-secondary"
              onClick={() => onNavigate('findings')}
            >
              <IconShieldAlert size={15} />
              <span>Review 5 Security Findings</span>
            </button>
          </div>
        </div>
      ) : (
        <div className="overview-demo-prompt-banner">
          <div className="demo-prompt-left">
            <span className="demo-prompt-badge">⚡ Quick Preview Available</span>
            <h3 className="demo-prompt-title">Explore the E-Commerce Demo Project</h3>
            <p className="demo-prompt-desc">
              Open a pre-seeded multi-service architecture with 8 microservices and security findings without manual setup.
            </p>
          </div>
          <button
            type="button"
            className="btn-open-demo-prompt"
            onClick={onOpenDemo}
          >
            <span>Open Demo Project &rarr;</span>
          </button>
        </div>
      )}

      {/* Real Repository Analysis Callout Banner */}
      <div className="overview-real-repo-banner">
        <div className="real-repo-left">
          <span className="real-repo-badge">Real Repository Analysis</span>
          <h3 className="real-repo-title">Analyze Real Codebases &amp; Calculate Revision Blast Radius</h3>
          <p className="real-repo-desc">
            Import any public/private GitHub repository or your local workspace. Static AST parsers extract Spring Boot
            and Express components, resolve dependency injection graph edges, catalog exposed REST APIs, and calculate change blast radius from Git diffs.
          </p>
        </div>
        <div className="real-repo-actions">
          {onOpenImportModal && (
            <button
              type="button"
              className="btn-real-repo-primary"
              onClick={onOpenImportModal}
            >
              <span>📦 Import Real Repository &rarr;</span>
            </button>
          )}
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="overview-kpi-grid">
        <div className="kpi-card" onClick={() => onNavigate('services')} role="button" tabIndex={0}>
          <div className="kpi-icon-wrap icon-blue">
            <IconServices size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{services.length}</span>
            <span className="kpi-label">Active Microservices</span>
            <span className="kpi-hint">Across 4 architecture tiers</span>
          </div>
        </div>

        <div className="kpi-card" onClick={() => onNavigate('topology')} role="button" tabIndex={0}>
          <div className="kpi-icon-wrap icon-purple">
            <IconTopology size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{dependencies.length}</span>
            <span className="kpi-label">Dependency Edges</span>
            <span className="kpi-hint">{isCircular ? 'Circular Loop Detected' : 'Acyclic DAG'}</span>
          </div>
        </div>

        <div className="kpi-card" onClick={() => onNavigate('findings')} role="button" tabIndex={0}>
          <div className="kpi-icon-wrap icon-red">
            <IconShieldAlert size={22} />
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{findings.length}</span>
            <span className="kpi-label">Security Findings</span>
            <span className="kpi-hint">
              <strong className="text-danger">{criticalFindings.length} Crit</strong> •{' '}
              <strong className="text-warning">{highFindings.length} High</strong> •{' '}
              <span>{mediumFindings.length} Med ({openFindings.length} active)</span>
            </span>
          </div>
        </div>

        <div className="kpi-card" onClick={() => onNavigate('topology')} role="button" tabIndex={0}>
          <div className={`kpi-icon-wrap ${isCircular ? 'icon-amber' : 'icon-green'}`}>
            {isCircular ? <IconAlertTriangle size={22} /> : <IconCheckShield size={22} />}
          </div>
          <div className="kpi-body">
            <span className="kpi-value">{isCircular ? 'Warning' : 'Healthy'}</span>
            <span className="kpi-label">Architecture Status</span>
            <span className="kpi-hint">{isCircular ? 'Cycle feedback loops present' : 'Downstream flow verified'}</span>
          </div>
        </div>
      </div>

      {/* Main Split Grid */}
      <div className="overview-grid-split">
        {/* Left Column: Security Findings & Quick Actions */}
        <div className="overview-col-left">
          {/* Security Posture Summary Card */}
          <div className="overview-card">
            <div className="overview-card-header">
              <div className="card-title-group">
                <IconShieldAlert size={18} className="text-danger" />
                <h3 className="card-title">Security & Architecture Drift Findings</h3>
              </div>
              <button
                type="button"
                className="card-action-link"
                onClick={() => onNavigate('findings')}
              >
                <span>View All ({findings.length})</span>
                <IconChevronRight size={14} />
              </button>
            </div>

            <div className="findings-mini-list">
              {findings.slice(0, 4).map((f) => (
                <div
                  key={f.id}
                  className="finding-mini-item"
                  onClick={() => onNavigate('findings')}
                  role="button"
                  tabIndex={0}
                >
                  <div className="mini-item-left">
                    <span className={`severity-tag tag-${f.severity.toLowerCase()}`}>
                      {f.severity}
                    </span>
                    <div className="mini-item-info">
                      <span className="mini-item-title">{f.title}</span>
                      <span className="mini-item-meta">
                        {f.ruleId} • {f.affectedComponent || 'Core'} • {f.affectedEndpoint || 'Internal'}
                      </span>
                    </div>
                  </div>
                  <span className={`status-pill pill-${f.status.toLowerCase()}`}>
                    {f.status}
                  </span>
                </div>
              ))}
              {findings.length === 0 && (
                <div className="empty-mini-state">
                  <IconCheckShield size={24} className="text-success" />
                  <span>No open security findings detected</span>
                </div>
              )}
            </div>
          </div>

          {/* Quick Simulation Scenarios */}
          <div className="overview-card">
            <div className="overview-card-header">
              <div className="card-title-group">
                <IconImpact size={18} className="text-primary" />
                <h3 className="card-title">Simulate Change Blast Radius</h3>
              </div>
            </div>
            <p className="card-desc">
              Select a microservice to immediately load its dependency graph and evaluate blast radius in Impact Studio:
            </p>
            <div className="quick-services-chips">
              {services.slice(0, 6).map((svc) => (
                <button
                  type="button"
                  key={svc.id}
                  className="quick-service-btn"
                  onClick={() => onSelectServiceAndStudio(svc.id)}
                  title={`Simulate change to ${svc.label || svc.id}`}
                >
                  <ServiceIcon name={svc.icon || 'database'} size={15} />
                  <span>{svc.label || svc.id}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Right Column: Recent Runs & Recent Activity */}
        <div className="overview-col-right">
          {/* Recent Analysis Runs */}
          <div className="overview-card">
            <div className="overview-card-header">
              <div className="card-title-group">
                <IconHistory size={18} className="text-primary" />
                <h3 className="card-title">Recent Analysis Runs</h3>
              </div>
              <button
                type="button"
                className="card-action-link"
                onClick={() => onNavigate('history')}
              >
                <span>History</span>
                <IconChevronRight size={14} />
              </button>
            </div>
            <div className="mini-runs-list">
              {historyRuns.slice(0, 3).map((run) => (
                <div key={run.id} className="mini-run-row">
                  <div className="run-row-header">
                    <span className="run-type-badge">{run.runType}</span>
                    <span className="run-time">
                      {run.timestamp ? new Date(run.timestamp).toLocaleTimeString() : 'Recent'}
                    </span>
                  </div>
                  <p className="run-summary-snippet">{run.summary}</p>
                  <div className="run-meta-line">
                    <span>Target: <code>{run.commitSha || run.targetRevision || 'HEAD'}</code></span>
                    <span className="run-finding-count">{run.totalFindings} findings</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Recent Audit Telemetry */}
          <div className="overview-card">
            <div className="overview-card-header">
              <div className="card-title-group">
                <IconCode size={18} className="text-primary" />
                <h3 className="card-title">Recent Security Audit Trail</h3>
              </div>
              <button
                type="button"
                className="card-action-link"
                onClick={() => onNavigate('audit')}
              >
                <span>Audit Explorer</span>
                <IconChevronRight size={14} />
              </button>
            </div>
            <div className="mini-audit-list">
              {auditEvents.slice(0, 4).map((evt) => (
                <div key={evt.id} className="mini-audit-row">
                  <div className="audit-left">
                    <span className={`audit-dot dot-${evt.outcome === 'SUCCESS' ? 'ok' : 'fail'}`} />
                    <div className="audit-info">
                      <span className="audit-event-type">{evt.eventType}</span>
                      <span className="audit-desc-text">{evt.description}</span>
                    </div>
                  </div>
                  <span className="audit-actor">{evt.actorEmail?.split('@')[0] || 'system'}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
