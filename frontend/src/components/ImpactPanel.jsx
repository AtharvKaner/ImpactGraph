import React, { useMemo } from 'react';
import {
  IconBarChart,
  IconLink,
  IconImpact,
  IconCheckShield,
  IconAlertTriangle,
  ServiceIcon,
} from './Icons';

/**
 * Change Impact Panel displaying simulation results.
 *
 * Clearly labeled as a simulated change against the selected demonstration architecture.
 * Displays:
 * - Direct vs indirect (transitive) affected services
 * - Explanatory dependency paths in ordered sequence
 * - Architecture warnings (e.g. circular dependency loops)
 */
export function ImpactPanel({
  proposedServiceId = '',
  analyzedServiceId = '',
  services = [],
  impactResult = null,
  loading = false,
  onAnalyze,
  onSelectService,
  currentPresetName = 'Demonstration Architecture',
  findings = [],
}) {
  const serviceMap = useMemo(() => {
    const map = new Map();
    for (const s of services) {
      map.set(s.id, s);
    }
    return map;
  }, [services]);

  const selectedService = proposedServiceId ? serviceMap.get(proposedServiceId) : null;
  const isAnalyzed = Boolean(impactResult && analyzedServiceId && analyzedServiceId === proposedServiceId);

  // Compute relevant security findings within blast radius
  const relevantFindings = useMemo(() => {
    if (!findings || findings.length === 0 || !analyzedServiceId) return [];
    const affectedSet = new Set([
      analyzedServiceId,
      ...(impactResult?.directAffectedServices || []),
      ...(impactResult?.transitiveAffectedServices || []),
    ]);
    return findings.filter((f) => {
      const comp = f.affectedComponent;
      if (!comp) return false;
      return affectedSet.has(comp) ||
        comp.toLowerCase().includes(analyzedServiceId.toLowerCase()) ||
        analyzedServiceId.toLowerCase().includes(comp.toLowerCase());
    });
  }, [findings, analyzedServiceId, impactResult]);

  // Compute targeted regression test recommendations derived from actual analyzed services
  const recommendedTests = useMemo(() => {
    if (!analyzedServiceId) return [];
    if (impactResult?.recommendedRegressionTests?.length > 0) {
      return impactResult.recommendedRegressionTests.map((t) => ({
        name: t,
        reason: 'Targeted regression test candidate for components in the change blast radius',
      }));
    }
    const tests = [
      { name: `${analyzedServiceId}Test`, reason: `Direct unit test suite for ${analyzedServiceId}` },
      { name: `${analyzedServiceId}IntegrationTest`, reason: `Integration verification suite for callers and dependencies of ${analyzedServiceId}` },
    ];
    for (const caller of (impactResult?.directAffectedServices || []).slice(0, 2)) {
      tests.push({ name: `${caller}IntegrationTest`, reason: `Downstream contract test for direct dependent ${caller}` });
    }
    return tests;
  }, [analyzedServiceId, impactResult]);

  // 1. Loading State
  if (loading) {
    return (
      <div className="impact-panel-card loading-state">
        <div className="spinner" />
        <p className="loading-text">
          Computing change blast radius across dependency graph...
        </p>
        <span className="loading-subtext">Evaluating reverse dependencies across architectural call graph</span>
      </div>
    );
  }

  // 2. Empty State (No Service Selected)
  if (!proposedServiceId) {
    const isDemo = Boolean(currentPresetName?.includes('Demo') || currentPresetName?.includes('Demonstration') || currentPresetName?.includes('E-Commerce'));
    return (
      <div className="impact-panel-card empty-state">
        <div className="empty-icon-wrap">
          <IconBarChart size={32} className="text-muted" />
        </div>
        <h4 className="empty-title">Component Blast Radius Analysis</h4>
        <p className="empty-description">
          Select a component from the control bar above or click any node in the dependency graph, then click <strong>Analyze Impact</strong> to evaluate its downstream blast radius.
        </p>

        {/* Suggested Simulation Scenarios based on active architecture */}
        <div className="suggested-scenarios-card">
          <span className="scenarios-heading">Suggested Analysis Candidates:</span>
          <div className="scenario-chips-row">
            {services.slice(0, 3).map((svc) => (
              <button
                key={svc.id}
                type="button"
                className="scenario-chip"
                onClick={() => onSelectService(svc.id)}
                title={`Analyze change impact on ${svc.label || svc.id}`}
              >
                <strong>{svc.label || svc.id}</strong>
                <span>{svc.tier || svc.type || 'Component'}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="empty-tip-card">
          <span className="empty-tip-badge">Evidence Note</span>
          <span>
            {isDemo
              ? 'Evaluating reachability on the multi-tier reference architecture. Switch to Git Revision Comparison to analyze real repository diffs.'
              : 'Blast radius is computed from static AST dependencies and exposed routes in this repository.'}
          </span>
        </div>
      </div>
    );
  }

  // 3. Pending Analysis State (Service selected, but user hasn't clicked "Analyze Impact" yet)
  if (!isAnalyzed) {
    return (
      <div className="impact-panel-card pending-state">
        <div className="panel-title-group">
          <div className="panel-title-icon">
            <IconImpact size={18} className="text-primary" />
          </div>
          <div>
            <h3 className="section-title">Ready to Analyze</h3>
            <p className="panel-subtitle">Simulated change target is selected</p>
          </div>
        </div>

        <div className="selected-service-card pending-card">
          <div className="selected-service-left">
            <div className="selected-service-icon-box">
              <ServiceIcon name={selectedService?.icon || 'database'} size={20} className="icon-amber" />
            </div>
            <div className="selected-service-text-wrap">
              <span className="selected-service-name">
                {selectedService?.label || proposedServiceId}
              </span>
              <span className="selected-service-meta">
                {selectedService?.tier} • {selectedService?.type}
              </span>
            </div>
          </div>
        </div>

        {selectedService?.description && (
          <p className="service-desc-snippet">{selectedService.description}</p>
        )}

        <div className="pending-cta-box">
          <p className="pending-cta-text">
            Click <strong>Analyze Impact</strong> to compute downstream affected services and dependency paths across <strong>{currentPresetName}</strong>.
          </p>
          <button
            type="button"
            className="analyze-impact-btn btn-large"
            onClick={onAnalyze}
          >
            <IconImpact size={16} className="analyze-btn-icon" />
            <span>Analyze Impact for {selectedService?.label || proposedServiceId}</span>
          </button>
        </div>
      </div>
    );
  }

  // 4. Analysis Results State
  const targetId = impactResult?.changedServiceId || proposedServiceId;
  const directList = (impactResult?.directAffectedServices || []).filter((id) => id !== targetId);
  const transitiveList = (impactResult?.transitiveAffectedServices || []).filter((id) => id !== targetId);
  const paths = (impactResult?.impactPaths || []).filter((p) => p.affectedServiceId !== targetId);
  const totalAffected = impactResult?.affectedCount ?? (directList.length + transitiveList.length);
  const cycleResult = impactResult?.cycleResult;
  const hasCycle = cycleResult?.hasCycle;

  return (
    <div className="impact-panel-card">
      {/* Panel Header */}
      <div className="impact-panel-header">
        <div className="panel-title-group">
          <div className="panel-title-icon">
            <IconBarChart size={18} className="text-primary" />
          </div>
          <div>
            <h3 className="section-title">Simulated Change Impact</h3>
            <p className="panel-subtitle">
              Evaluated against <strong>{currentPresetName}</strong>
            </p>
          </div>
        </div>
        <span className="simulation-badge-subtle">Simulated Evaluation</span>
      </div>

      {/* Selected Target Service Card */}
      <div className="selected-service-card">
        <div className="selected-service-left">
          <div className="selected-service-icon-box">
            <ServiceIcon name={selectedService?.icon || 'database'} size={18} className="icon-amber" />
          </div>
          <div className="selected-service-text-wrap">
            <span className="target-label-caption">Target Service (Simulated Change):</span>
            <span className="selected-service-name">
              {selectedService?.label || proposedServiceId}
            </span>
          </div>
        </div>
        <span className="selected-service-tier">
          {selectedService?.tier || 'Domain Tier'}
        </span>
      </div>

      {/* Architecture Warning / Health Banner */}
      {hasCycle ? (
        <div className="panel-architecture-alert alert-warning">
          <IconAlertTriangle size={16} className="icon-alert" />
          <div>
            <strong>Architecture Notice (Circular Loop):</strong>
            <p>
              Circular dependency exists in topology: <code>{cycleResult.cycles[0]?.cycleString}</code>. Visited-node cycle tracking safely prunes circular paths.
            </p>
          </div>
        </div>
      ) : (
        <div className="panel-architecture-alert alert-safe">
          <IconCheckShield size={16} className="icon-safe" />
          <div>
            <strong>Architecture Notice (Acyclic DAG):</strong>
            <p>All dependencies flow downstream with zero circular feedback loops.</p>
          </div>
        </div>
      )}

      {/* 3 Metric Summary Cards */}
      <div className="impact-metrics-grid">
        <div className="impact-metric-card metric-total">
          <div className="metric-number">{totalAffected}</div>
          <div className="metric-label">Total Blast Radius</div>
          <div className="metric-sub">Direct + Indirect</div>
        </div>

        <div className="impact-metric-card metric-direct">
          <div className="metric-number">{directList.length}</div>
          <div className="metric-label">Direct Impact</div>
          <div className="metric-sub">1 dependency hop</div>
        </div>

        <div className="impact-metric-card metric-transitive">
          <div className="metric-number">{transitiveList.length}</div>
          <div className="metric-label">Indirect Impact</div>
          <div className="metric-sub">&gt; 1 transitive hop</div>
        </div>
      </div>

      {totalAffected === 0 ? (
        <div className="zero-impact-box">
          <div className="zero-impact-header">
            <span className="zero-badge">Zero Blast Radius</span>
          </div>
          <p className="zero-text">
            No other services depend on <code>{proposedServiceId}</code>. This service is a top-level dependent or leaf node; changes can proceed without downstream impact.
          </p>
        </div>
      ) : (
        <>
          {/* Direct Affected Services List */}
          <div className="impact-group-section">
            <div className="group-header">
              <span className="group-indicator indicator-direct" />
              <h4 className="group-title">
                Direct Affected Services ({directList.length})
              </h4>
            </div>

            {directList.length === 0 ? (
              <p className="group-empty-muted">None</p>
            ) : (
              <div className="affected-service-list">
                {directList.map((svcId) => {
                  const svc = serviceMap.get(svcId);
                  return (
                    <div key={svcId} className="affected-service-row row-direct">
                      <div className="row-left">
                        <div className="row-icon-box box-direct">
                          <ServiceIcon name={svc?.icon || 'cart'} size={16} className="icon-direct" />
                        </div>
                        <div className="row-info">
                          <span className="row-name">{svc?.label || svcId}</span>
                          <span className="row-meta">
                            {svc ? `${svc.tier} • ${svc.type}` : 'Application Tier'}
                          </span>
                        </div>
                      </div>
                      <span className="row-hop-badge badge-direct">1 hop</span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Indirect / Transitive Affected Services List */}
          <div className="impact-group-section">
            <div className="group-header">
              <span className="group-indicator indicator-transitive" />
              <h4 className="group-title">
                Indirect (Transitive) Affected Services ({transitiveList.length})
              </h4>
            </div>

            {transitiveList.length === 0 ? (
              <p className="group-empty-muted">None</p>
            ) : (
              <div className="affected-service-list">
                {transitiveList.map((svcId) => {
                  const svc = serviceMap.get(svcId);
                  const matchingPath = paths.find((p) => p.affectedServiceId === svcId);
                  const distance = matchingPath?.distance || 2;

                  return (
                    <div key={svcId} className="affected-service-row row-transitive">
                      <div className="row-left">
                        <div className="row-icon-box box-transitive">
                          <ServiceIcon name={svc?.icon || 'globe'} size={16} className="icon-transitive" />
                        </div>
                        <div className="row-info">
                          <span className="row-name">{svc?.label || svcId}</span>
                          <span className="row-meta">
                            {svc ? `${svc.tier} • ${svc.type}` : 'Client Tier'}
                          </span>
                        </div>
                      </div>
                      <span className="row-hop-badge badge-transitive">
                        {distance} hops
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Explanatory Dependency Paths */}
          <div className="impact-group-section">
            <div className="group-header">
              <span className="group-icon-link">
                <IconLink size={15} />
              </span>
              <h4 className="group-title">
                Explanatory Dependency Paths ({paths.length})
              </h4>
            </div>
            <p className="paths-caption">
              Shows how each affected service depends upstream until reaching the changed service.
            </p>

            <div className="paths-stack">
              {paths.map((p, idx) => {
                const isDirect = p.isDirect || p.distance === 1;
                return (
                  <div key={p.affectedServiceId + idx} className="path-chain-card">
                    <div className="path-chain-left">
                      <span className={`path-step-num ${isDirect ? 'num-direct' : 'num-transitive'}`}>
                        {idx + 1}
                      </span>
                      <div className="path-chips-row">
                        {p.path.map((node, i) => {
                          const isTarget = i === p.path.length - 1;
                          const isDirectHop = i === p.path.length - 2;
                          let chipClass = 'chip-transitive';
                          if (isTarget) {
                            chipClass = 'chip-target';
                          } else if (isDirectHop) {
                            chipClass = 'chip-direct';
                          }

                          return (
                            <React.Fragment key={i}>
                              <span className={`path-chip ${chipClass}`}>
                                {node}
                              </span>
                              {i < p.path.length - 1 && (
                                <span className="path-arrow">&rarr;</span>
                              )}
                            </React.Fragment>
                          );
                        })}
                      </div>
                    </div>
                    <span className="path-distance-pill">
                      {p.distance} {p.distance === 1 ? 'hop' : 'hops'}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Security Findings Amplified by Blast Radius */}
          <div className="impact-group-section">
            <div className="group-header">
              <span className="group-indicator indicator-direct" />
              <h4 className="group-title">
                Security Findings in Blast Radius ({relevantFindings.length})
              </h4>
            </div>
            {relevantFindings.length > 0 ? (
              <div className="impact-findings-list">
                {relevantFindings.map((f) => (
                  <div key={f.id} className="impact-finding-card">
                    <div className="impact-finding-head">
                      <span className={`severity-tag tag-${f.severity.toLowerCase()}`}>{f.severity}</span>
                      <span className="impact-finding-rule">{f.ruleId}</span>
                      <span className="impact-finding-comp">{f.affectedComponent}</span>
                    </div>
                    <span className="impact-finding-title">{f.title}</span>
                    <p className="impact-finding-desc">{f.description}</p>
                    {f.suggestedRemediation && (
                      <div className="impact-finding-remediation">
                        <strong>Remediation:</strong> {f.suggestedRemediation}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="group-empty-muted">Zero known security findings identified along this component's dependency paths.</p>
            )}
          </div>

          {/* Recommended Targeted Regression Tests */}
          <div className="impact-group-section">
            <div className="group-header">
              <span className="group-indicator indicator-transitive" />
              <h4 className="group-title">
                Recommended Targeted Regression Tests ({recommendedTests.length})
              </h4>
            </div>
            <div className="impact-tests-list">
              {recommendedTests.map((t, idx) => (
                <div key={idx} className="impact-test-item">
                  <span className="test-icon">🧪</span>
                  <div className="test-details">
                    <code className="test-name">{t.name}</code>
                    <span className="test-reason">{t.reason}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
