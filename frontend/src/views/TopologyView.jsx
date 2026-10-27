import React, { useMemo } from 'react';
import {
  IconCheckShield,
  IconAlertTriangle,
  IconImpact,
  ServiceIcon,
} from '../components/Icons';

export function TopologyView({
  services = [],
  dependencies = [],
  graphData,
  onSelectServiceAndStudio,
  selectedProjectId,
  currentPreset,
}) {
  const cycles = graphData?.cycles || graphData?.cycleDetails || [];
  const hasCycle = Boolean(graphData?.hasCycle);

  // Calculate degree centrality (most connected components)
  const centralityList = useMemo(() => {
    const map = new Map();
    for (const s of services) {
      map.set(s.id, { service: s, inDegree: 0, outDegree: 0, totalDegree: 0 });
    }
    for (const d of dependencies) {
      const src = d.source || d.sourceServiceName;
      const tgt = d.target || d.targetServiceName;
      if (map.has(src)) {
        map.get(src).outDegree += 1;
        map.get(src).totalDegree += 1;
      }
      if (map.has(tgt)) {
        map.get(tgt).inDegree += 1;
        map.get(tgt).totalDegree += 1;
      }
    }
    return Array.from(map.values()).sort((a, b) => b.totalDegree - a.totalDegree);
  }, [services, dependencies]);

  // Defined architecture health metric formulas
  const serviceCount = services.length || 1;
  const edgeCount = dependencies.length;
  const density = (edgeCount / (serviceCount * (serviceCount - 1) || 1)).toFixed(3);
  const couplingRatio = (edgeCount / serviceCount).toFixed(2);
  const healthScore = Math.max(0, 100 - (hasCycle ? 35 : 0) - Math.round(parseFloat(couplingRatio) * 10));

  return (
    <div className="view-container topology-view">
      <div className="view-header-row">
        <div>
          <h2 className="view-title">Topology & Architecture Health Analyzer</h2>
          <p className="view-subtitle">
            Cyclomatic cycle detection, graph centrality scores, and coupling ratio metrics for{' '}
            <strong>{selectedProjectId ? (graphData?.name || `Repository Project #${selectedProjectId}`) : (graphData?.name || currentPreset)}</strong>
          </p>
        </div>
      </div>

      {/* Architecture Health Posture Card */}
      <div className={`topology-health-card-large ${hasCycle ? 'posture-warning' : 'posture-safe'}`}>
        <div className="posture-left">
          <div className={`posture-icon-wrap ${hasCycle ? 'box-alert' : 'box-safe'}`}>
            {hasCycle ? <IconAlertTriangle size={28} /> : <IconCheckShield size={28} />}
          </div>
          <div className="posture-text">
            <h3 className="posture-title">
              {hasCycle
                ? `Circular Dependency Loop Detected (${cycles.length} active ${cycles.length === 1 ? 'cycle' : 'cycles'})`
                : 'Topology Verified: Directed Acyclic Graph (DAG)'}
            </h3>
            <p className="posture-desc">
              {hasCycle
                ? 'Back-edge dependency cycles create tight coupling between tiers, leading to cascading failures, complex startup ordering, and distributed deadlocks.'
                : 'All microservice dependencies flow unidirectionally downstream. The architecture allows isolated deployment, simple startup sequencing, and clean failure domains.'}
            </p>
          </div>
        </div>

        <div className="posture-health-metric">
          <div className="health-score-dial">
            <span className="dial-num">{healthScore}</span>
            <span className="dial-max">/100</span>
          </div>
          <span className="dial-label">Architecture Health Score</span>
        </div>
      </div>

      {/* Metrics Row */}
      <div className="topology-metrics-row">
        <div className="top-metric-card">
          <span className="metric-val">{serviceCount}</span>
          <span className="metric-title">Nodes (Services)</span>
          <span className="metric-sub">Active components</span>
        </div>
        <div className="top-metric-card">
          <span className="metric-val">{edgeCount}</span>
          <span className="metric-title">Directed Edges</span>
          <span className="metric-sub">A &rarr; B dependency relations</span>
        </div>
        <div className="top-metric-card">
          <span className="metric-val">{couplingRatio}</span>
          <span className="metric-title">Coupling Ratio</span>
          <span className="metric-sub">Formula: Edges &divide; Services</span>
        </div>
        <div className="top-metric-card">
          <span className="metric-val">{density}</span>
          <span className="metric-title">Graph Density</span>
          <span className="metric-sub">Formula: |E| &divide; (|V|*(|V|-1))</span>
        </div>
      </div>

      {/* Cycles Detail Section */}
      {hasCycle && (
        <div className="topology-section-card">
          <div className="section-head">
            <IconAlertTriangle size={18} className="text-warning" />
            <h3 className="section-title">Detected Circular Dependency Loops ({cycles.length})</h3>
          </div>
          <p className="section-caption">
            Tarjan strongly-connected component (SCC) and visited-node DFS detected the following circular back-edges:
          </p>

          <div className="cycle-details-list">
            {cycles.map((c, idx) => (
              <div key={idx} className="cycle-detail-box">
                <div className="cycle-box-head">
                  <span className="cycle-tag">Cycle #{idx + 1}</span>
                  <span className="cycle-length">{c.length || (c.cycleNodes || []).length} Hops in loop</span>
                </div>
                <div className="cycle-path-display">
                  <code>{c.cycleString || (c.cycleNodes || []).join(' → ')}</code>
                </div>
                <p className="cycle-remediation-tip">
                  <strong>Recommended Architectural Decoupling:</strong> Break this circular dependency by introducing asynchronous event messaging (e.g. RabbitMQ/Kafka) or extracting shared domain types into an independent lower-tier service.
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Centrality & High-Impact Components */}
      <div className="topology-section-card">
        <div className="section-head">
          <IconImpact size={18} className="text-primary" />
          <h3 className="section-title">High-Centrality Components (Maximum Blast Radius)</h3>
        </div>
        <p className="section-caption">
          Services with the highest incoming and outgoing connections represent structural bottlenecks and have the widest blast radius when modified:
        </p>

        <div className="centrality-table-wrapper">
          <table className="centrality-table">
            <thead>
              <tr>
                <th>Service</th>
                <th>Tier</th>
                <th>In-Degree (Dependents)</th>
                <th>Out-Degree (Dependencies)</th>
                <th>Total Degree</th>
                <th>Blast Risk</th>
              </tr>
            </thead>
            <tbody>
              {centralityList.slice(0, 6).map((c) => {
                const riskLevel = c.inDegree >= 3 ? 'HIGH' : (c.inDegree >= 1 ? 'MEDIUM' : 'LOW');
                return (
                  <tr
                    key={c.service.id}
                    className="clickable-row"
                    onClick={() => onSelectServiceAndStudio && onSelectServiceAndStudio(c.service.id)}
                    title={`Click to analyze change impact for ${c.service.label || c.service.id} in Studio`}
                  >
                    <td>
                      <div className="table-service-cell">
                        <ServiceIcon name={c.service.icon || 'database'} size={15} />
                        <strong>{c.service.label || c.service.name || c.service.id}</strong>
                      </div>
                    </td>
                    <td><span className="table-tier-badge">{c.service.tier || 'Domain Tier'}</span></td>
                    <td><strong>{c.inDegree}</strong> callers</td>
                    <td>{c.outDegree} downstream</td>
                    <td><strong>{c.totalDegree}</strong> connections</td>
                    <td>
                      <span className={`risk-pill pill-${riskLevel.toLowerCase()}`}>
                        {riskLevel}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
