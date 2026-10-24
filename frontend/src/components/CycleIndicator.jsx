import React from 'react';
import {
  IconCheckShield,
  IconAlertTriangle,
} from './Icons';

export function CycleIndicator({ cycleResult, presetName }) {
  const hasCycle = cycleResult?.hasCycle;
  const cycles = cycleResult?.cycles || [];

  return (
    <div className={`topology-health-card ${hasCycle ? 'health-warning' : 'health-safe'}`}>
      <div className="health-left-block">
        <div className={`health-icon-box ${hasCycle ? 'box-alert' : 'box-safe'}`}>
          {hasCycle ? (
            <IconAlertTriangle size={20} className="icon-alert" />
          ) : (
            <IconCheckShield size={20} className="icon-safe" />
          )}
        </div>
        <div className="health-text-wrap">
          <h4 className="health-title">
            {hasCycle
              ? `Circular Dependency Detected (${cycles.length} ${cycles.length === 1 ? 'cycle' : 'cycles'})`
              : 'Topology Safe'}
          </h4>
          <p className="health-subtitle">
            {hasCycle
              ? 'Circular feedback loop detected. Visited-node tracking isolates cycles during impact analysis.'
              : 'No circular dependencies detected in the current topology.'}
          </p>
        </div>
      </div>

      <div className="health-divider" />

      <div className="health-right-block">
        <div className="health-mode-text">
          <h4 className="health-mode-title">
            {hasCycle ? 'Circular Feedback Loop' : 'Acyclic DAG'}
          </h4>
          <p className="health-mode-subtitle">
            {hasCycle
              ? `Back-edge present in ${presetName || 'topology'}: Payment Service ↔ Notification Service.`
              : `All dependencies in ${presetName || 'topology'} flow downstream without cycles.`}
          </p>
        </div>

        {hasCycle && cycles.length > 0 && (
          <div className="cycle-chips-wrapper">
            {cycles.map((c, idx) => (
              <span key={idx} className="cycle-path-chip" title="Detected Cycle">
                <strong>Cycle #{idx + 1}:</strong> {c.cycleString} ({c.length} hops)
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
