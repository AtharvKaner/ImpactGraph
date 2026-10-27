import React from 'react';
import { DATASET_PRESETS } from '../data/mockServicesData';
import {
  IconClear,
  IconChevronDown,
  IconImpact,
  IconCheckShield,
  IconAlertTriangle,
  ServiceIcon,
} from './Icons';

/**
 * Control bar separating Architecture Setup from Change Simulation.
 *
 * Workflow:
 * 1. Select Demonstration Architecture (Acyclic DAG vs Circular Loop)
 * 2. Select Proposed Service to Change
 * 3. Click "Analyze Impact" to execute blast-radius simulation
 */
export function ServiceSelector({
  services = [],
  proposedServiceId = '',
  onSelectService,
  onAnalyze,
  currentPreset = DATASET_PRESETS.ECOMMERCE_DAG,
  onChangePreset,
  projects = [],
  selectedProjectId = null,
  onSelectProject,
  loading = false,
  hasAnalyzed = false,
  edgeCount = 10,
  hasCycle = false,
  onOpenImportModal = null,
}) {
  const selectedService = services.find((s) => s.id === proposedServiceId);
  const isPendingAnalysis = proposedServiceId && !hasAnalyzed;

  const selectedProject = projects.find((p) => p.id === selectedProjectId);
  const demoProject = projects.find((p) => p.name && p.name.includes('[Demo]'));
  const isDemoProjectActive = Boolean(
    (!selectedProjectId && currentPreset === DATASET_PRESETS.ECOMMERCE_DAG) ||
    (selectedProject?.name && selectedProject.name.includes('[Demo]'))
  );

  return (
    <div className="control-bar-card">
      {/* 1. Architecture Setup Group */}
      <div className="control-section architecture-setup-section">
        <div className="section-label-row">
          <span className="step-tag">Step 1</span>
          <span className="control-label">
            {selectedProjectId ? 'Persisted Architecture' : 'Demonstration Architecture'}
          </span>
          {isDemoProjectActive ? (
            <span className="simulation-notice-pill" title="Interactive 8-service multi-tier demonstration project">
              Demo Active
            </span>
          ) : selectedProjectId ? (
            <span className="persisted-notice-pill" title="Analyzed repository architecture">
              Repository Project
            </span>
          ) : (
            <span className="simulation-notice-pill" title="Pre-configured demonstration dataset">
              Demo Preset
            </span>
          )}
        </div>
        <div className="architecture-controls-row">
          <div className="segmented-toggle" role="group" aria-label="Demonstration Architecture">
            <button
              type="button"
              className={`toggle-btn ${isDemoProjectActive ? 'active' : ''}`}
              onClick={() => {
                if (demoProject) {
                  onSelectProject(demoProject.id);
                } else {
                  onChangePreset(DATASET_PRESETS.ECOMMERCE_DAG);
                }
              }}
              disabled={loading}
              title="Standard 8-service e-commerce microservices (Acyclic DAG)"
            >
              E-Commerce (DAG)
            </button>
            <button
              type="button"
              className={`toggle-btn ${!selectedProjectId && currentPreset === DATASET_PRESETS.CIRCULAR_DEMO ? 'active' : ''}`}
              onClick={() => onChangePreset(DATASET_PRESETS.CIRCULAR_DEMO)}
              disabled={loading}
              title="E-commerce topology with circular dependency loop"
            >
              Circular Feedback Demo
            </button>
          </div>

          {/* Persisted Projects Selector (shown when projects are available) */}
          {projects.length > 0 && (
            <div className="custom-select-container project-select-box">
              <select
                id="project-select"
                value={selectedProjectId || ''}
                onChange={(e) => {
                  const val = e.target.value;
                  if (val) {
                    onSelectProject(Number(val));
                  } else {
                    onChangePreset(DATASET_PRESETS.ECOMMERCE_DAG);
                  }
                }}
                disabled={loading}
                className="service-native-select"
                aria-label="Select Persisted Project"
              >
                <option value="">-- Or choose Persisted Project --</option>
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} (Project #{p.id})
                  </option>
                ))}
              </select>
              <div className="custom-select-arrow">
                <IconChevronDown size={14} />
              </div>
            </div>
          )}

          {onOpenImportModal && (
            <button
              type="button"
              className="btn-import-project-chip"
              onClick={onOpenImportModal}
              title="Import a new repository for architecture analysis"
            >
              <span>📦 Import Repo</span>
            </button>
          )}

          <div className="architecture-meta-badge" title="Architecture summary">
            {selectedProjectId ? (
              <span className="meta-badge-item badge-persisted">
                <IconCheckShield size={13} className="text-primary" />
                Repository Architecture ({services.length} services • {edgeCount} edges)
              </span>
            ) : hasCycle ? (
              <span className="meta-badge-item badge-warning">
                <IconAlertTriangle size={13} className="text-amber" />
                Circular Loop ({services.length} services • {edgeCount} edges)
              </span>
            ) : (
              <span className="meta-badge-item badge-safe">
                <IconCheckShield size={13} className="text-safe" />
                Acyclic DAG ({services.length} services • {edgeCount} edges)
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="control-divider" />

      {/* 2. Change Simulation Setup Group */}
      <div className="control-section simulation-setup-section">
        <div className="section-label-row">
          <span className="step-tag step-tag-accent">Step 2</span>
          <span className="control-label">Simulate Software Change</span>
          <span className="simulation-notice-pill" title="This is a simulated change evaluation on the demonstration architecture">
            Simulated Change
          </span>
        </div>

        <div className="simulation-controls-row">
          {/* Target Service Selector Dropdown */}
          <div className="custom-select-container">
            <div className="custom-select-icon">
              <ServiceIcon
                name={selectedService?.icon || 'database'}
                size={16}
                className={selectedService ? 'icon-amber' : 'icon-muted'}
              />
            </div>
            <select
              id="service-select"
              value={proposedServiceId}
              onChange={(e) => onSelectService(e.target.value)}
              disabled={loading}
              className="service-native-select"
              aria-label="Select Proposed Service to Change"
            >
              <option value="">-- Choose service to simulate change --</option>
              {services.map((svc) => (
                <option key={svc.id} value={svc.id}>
                  {svc.label} ({svc.tier})
                </option>
              ))}
            </select>
            <div className="custom-select-arrow">
              <IconChevronDown size={14} />
            </div>
          </div>

          {/* Explicit "Analyze Impact" Primary Action Button */}
          <button
            type="button"
            className={`analyze-impact-btn ${isPendingAnalysis ? 'btn-highlight' : ''}`}
            onClick={onAnalyze}
            disabled={!proposedServiceId || loading}
            title={
              !proposedServiceId
                ? 'Select a service to simulate change first'
                : 'Compute change impact blast radius across dependency graph'
            }
          >
            {loading ? (
              <>
                <span className="spinner-inline" />
                <span>Analyzing...</span>
              </>
            ) : (
              <>
                <IconImpact size={16} className="analyze-btn-icon" />
                <span>Analyze Impact</span>
              </>
            )}
          </button>

          {/* Reset / Clear Button */}
          {proposedServiceId && (
            <button
              type="button"
              className="clear-selection-btn"
              onClick={() => onSelectService('')}
              title="Reset change simulation"
              disabled={loading}
            >
              <IconClear size={14} className="clear-icon" />
              <span>Reset</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
