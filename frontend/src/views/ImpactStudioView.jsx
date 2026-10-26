import React, { useState, useMemo } from 'react';
import { ServiceSelector } from '../components/ServiceSelector';
import { CycleIndicator } from '../components/CycleIndicator';
import { GraphView } from '../components/GraphView';
import { ImpactPanel } from '../components/ImpactPanel';
import { RevisionComparisonPanel } from '../components/RevisionComparisonPanel';
import {
  IconSearch,
  IconClear,
  ServiceIcon,
} from '../components/Icons';

export function ImpactStudioView({
  services = [],
  dependencies = [],
  graphData,
  proposedServiceId,
  analyzedServiceId,
  impactResult,
  impactLoading,
  onSelectService,
  onAnalyze,
  currentPreset,
  onChangePreset,
  projects = [],
  selectedProjectId,
  onSelectProject,
  findings = [],
  onOpenImportModal = null,
}) {
  const [nodeSearch, setNodeSearch] = useState('');
  const [selectedTier, setSelectedTier] = useState('ALL');
  const [inspectedNodeId, setInspectedNodeId] = useState(null);
  const [analysisMode, setAnalysisMode] = useState('simulation'); // 'simulation' | 'revision'
  const [revisionBlastRadius, setRevisionBlastRadius] = useState(null);

  // Available architecture tiers
  const tiers = useMemo(() => ['ALL', ...new Set(services.map((service) => service.tier || 'Other'))], [services]);
  const isDemoProject = Boolean(graphData?.name?.includes('[Demo]'));

  // Effective graph impact result based on active mode
  const effectiveGraphImpactResult = useMemo(() => {
    if (analysisMode === 'revision' && revisionBlastRadius) {
      return {
        directlyChangedServices: revisionBlastRadius.directlyImpactedServices || [],
        directAffectedServices: revisionBlastRadius.directlyImpactedServices || [],
        transitiveAffectedServices: revisionBlastRadius.transitivelyImpactedServices || [],
        impactPaths: revisionBlastRadius.impactPaths || [],
      };
    }
    return impactResult;
  }, [analysisMode, revisionBlastRadius, impactResult]);

  // Filtered services based on search and tier
  const filteredServices = useMemo(() => {
    return services.filter((svc) => {
      const matchesSearch = !nodeSearch.trim() ||
        (svc.label || svc.name || svc.id).toLowerCase().includes(nodeSearch.toLowerCase()) ||
        (svc.type || '').toLowerCase().includes(nodeSearch.toLowerCase());
      const matchesTier = selectedTier === 'ALL' || svc.tier === selectedTier;
      return matchesSearch && matchesTier;
    });
  }, [services, nodeSearch, selectedTier]);

  const inspectedService = useMemo(() => {
    if (!inspectedNodeId) return null;
    return services.find((s) => s.id === inspectedNodeId);
  }, [services, inspectedNodeId]);

  const handleNodeClick = (nodeId) => {
    onSelectService(nodeId);
    setInspectedNodeId(nodeId);
  };

  return (
    <div className="view-container impact-studio-view">
      {/* Top Architecture & Change Control Bar */}
      <ServiceSelector
        services={services}
        proposedServiceId={proposedServiceId}
        onSelectService={onSelectService}
        onAnalyze={onAnalyze}
        currentPreset={currentPreset}
        onChangePreset={onChangePreset}
        projects={projects}
        selectedProjectId={selectedProjectId}
        onSelectProject={onSelectProject}
        loading={impactLoading}
        hasAnalyzed={Boolean(impactResult && analyzedServiceId && analyzedServiceId === proposedServiceId)}
        edgeCount={dependencies.length}
        hasCycle={graphData?.hasCycle}
        onOpenImportModal={onOpenImportModal}
      />

      {/* Cycle Indicator & Architecture Health Banner */}
      <CycleIndicator
        cycleResult={
          impactResult?.cycleResult || {
            hasCycle: graphData?.hasCycle,
            cycles: graphData?.cycles || graphData?.cycleDetails || [],
          }
        }
        presetName={graphData?.name || 'Current Topology'}
      />

      {/* Guided walkthrough is intentionally limited to the deterministic demo. */}
      {isDemoProject && <div className="guided-demo-banner">
        <div className="guided-banner-left">
          <div className="guided-icon-wrap">
            <IconSearch size={18} />
          </div>
          <div className="guided-texts">
            <span className="guided-title">Guided Walkthrough Scenario: Payment Service Blast Radius</span>
            <span className="guided-steps">
              Simulate changes to <strong>Payment Service</strong> &rarr; Observe direct dependents (Order &amp; Checkout Services), transitive caller (Storefront Web), and amplify SEC-AUTHZ-002 refund vulnerability.
            </span>
          </div>
        </div>
        <button
          type="button"
          className="btn-run-guided-workflow"
          onClick={() => {
            onSelectService('Payment Service');
            onAnalyze();
          }}
          disabled={impactLoading}
          title="Select Payment Service and execute blast radius simulation"
        >
          <span>▶ Run Payment Service Simulation</span>
        </button>
      </div>}

      {/* Analysis Mode Toggle: Single Component vs Git Revision Comparison */}
      <div className="analysis-mode-selector-bar">
        <div className="mode-toggle-group">
          <button
            type="button"
            className={`mode-toggle-btn ${analysisMode === 'simulation' ? 'active' : ''}`}
            onClick={() => setAnalysisMode('simulation')}
          >
            <span className="mode-icon">🎯</span>
            <span>Single Component Blast Radius</span>
          </button>
          <button
            type="button"
            className={`mode-toggle-btn ${analysisMode === 'revision' ? 'active' : ''}`}
            onClick={() => setAnalysisMode('revision')}
          >
            <span className="mode-icon">🔀</span>
            <span>Git Revision Comparison</span>
            {selectedProjectId && <span className="mode-active-pill">Git Mode</span>}
          </button>
        </div>

        {analysisMode === 'revision' && (
          <div className="mode-context-hint">
            <span>Analyzing repository diff &amp; blast radius across revisions.</span>
          </div>
        )}
      </div>

      {/* Graph Filter & Search Toolbar */}
      <div className="graph-filter-toolbar">
        <div className="filter-search-box">
          <IconSearch size={14} className="filter-search-icon" />
          <input
            type="text"
            className="filter-search-input"
            value={nodeSearch}
            onChange={(e) => setNodeSearch(e.target.value)}
            placeholder="Search nodes by name, tier, or type..."
          />
          {nodeSearch && (
            <button
              type="button"
              className="filter-search-clear"
              onClick={() => setNodeSearch('')}
              aria-label="Clear search"
            >
              <IconClear size={12} />
            </button>
          )}
        </div>

        <div className="tier-filter-chips">
          {tiers.map((t) => (
            <button
              key={t}
              type="button"
              className={`tier-chip ${selectedTier === t ? 'active' : ''}`}
              onClick={() => setSelectedTier(t)}
            >
              {t}
            </button>
          ))}
        </div>
        <span className="graph-filter-summary">Showing {filteredServices.length} of {services.length} components</span>
      </div>

      {/* Split Workbench: Graph Canvas + Impact Inspector */}
      <div className="dashboard-grid">
        <div className="graph-wrapper-col">
          <GraphView
            services={filteredServices}
            dependencies={dependencies}
            proposedServiceId={proposedServiceId}
            analyzedServiceId={analyzedServiceId}
            impactResult={effectiveGraphImpactResult}
            onNodeClick={handleNodeClick}
          />

          {/* Collapsible Inspected Node Details Drawer */}
          {inspectedService && (
            <div className="node-inspector-drawer">
              <div className="drawer-header">
                <div className="drawer-title-group">
                  <ServiceIcon name={inspectedService.icon || 'database'} size={18} />
                  <div>
                    <h4 className="drawer-title">{inspectedService.label || inspectedService.id}</h4>
                    <span className="drawer-subtitle">
                      {inspectedService.tier} • {inspectedService.type}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  className="drawer-close-btn"
                  onClick={() => setInspectedNodeId(null)}
                  aria-label="Close details"
                >
                  &times;
                </button>
              </div>
              <div className="drawer-body">
                <p className="drawer-desc">{inspectedService.description || 'No description provided.'}</p>
                <div className="drawer-stats">
                  <div className="drawer-stat-item">
                    <span className="stat-label">Role in Simulation:</span>
                    <span className="stat-val">
                      {proposedServiceId === inspectedService.id ? (
                        <strong className="text-amber">Selected Change Target</strong>
                      ) : (
                        'Dependency Node'
                      )}
                    </span>
                  </div>
                  <div className="drawer-stat-item">
                    <span className="stat-label">Incoming Dependents:</span>
                    <span className="stat-val">
                      {dependencies.filter((d) => (d.target || d.targetServiceName) === inspectedService.id).length} services
                    </span>
                  </div>
                  <div className="drawer-stat-item">
                    <span className="stat-label">Outgoing Dependencies:</span>
                    <span className="stat-val">
                      {dependencies.filter((d) => (d.source || d.sourceServiceName) === inspectedService.id).length} services
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Change Impact Inspector Panel vs Git Revision Comparison */}
        <div className="impact-inspector-col">
          {analysisMode === 'simulation' ? (
            <ImpactPanel
              proposedServiceId={proposedServiceId}
              analyzedServiceId={analyzedServiceId}
              services={services}
              impactResult={impactResult}
              loading={impactLoading}
              onAnalyze={onAnalyze}
              onSelectService={onSelectService}
              currentPresetName={graphData?.name}
              findings={findings}
            />
          ) : (
            <RevisionComparisonPanel
              selectedProjectId={selectedProjectId}
              projects={projects}
              onApplyBlastRadiusToGraph={(res) => setRevisionBlastRadius(res)}
              onSelectService={(svcName) => {
                onSelectService(svcName);
                setInspectedNodeId(svcName);
              }}
            />
          )}
        </div>
      </div>
    </div>
  );
}
