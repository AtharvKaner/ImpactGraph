import React, { useMemo, useState, useCallback, useEffect, useRef } from 'react';
import {
  ReactFlow,
  Background,
  MarkerType,
  ReactFlowProvider,
  useReactFlow,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { NODE_POSITIONS } from '../data/mockServicesData';
import { ServiceNode } from './ServiceNode';
import {
  IconImpact,
  IconPlus,
  IconMinus,
  IconFitView,
  IconLockControls,
  IconPlay,
  IconPause,
  IconChevronRight,
  IconRefresh,
  IconSearch,
} from './Icons';

const nodeTypes = {
  serviceNode: ServiceNode,
};

// Map known tiers to vertical layout layer indices
const TIER_LAYER_ORDER = {
  'client tier': 0,
  'frontend tier': 0,
  'web': 0,
  'gateway': 1,
  'application tier': 1,
  'api gateway tier': 1,
  'client / gateway tier': 1,
  'domain tier': 2,
  'core services': 2,
  'integration tier': 3,
  'messaging tier': 3,
  'infrastructure tier': 4,
  'data tier': 4,
  'security tier': 5,
  'compliance tier': 5,
};

function getTierLayer(tier) {
  if (!tier) return 2;
  const key = tier.toLowerCase().trim();
  for (const [pattern, layer] of Object.entries(TIER_LAYER_ORDER)) {
    if (key.includes(pattern)) return layer;
  }
  return 2;
}

function GraphInner({
  services = [],
  dependencies = [],
  proposedServiceId = '',
  analyzedServiceId = '',
  selectedServiceId = '',
  impactResult = null,
  onNodeClick,
  datasetKey = '',
  impactExplanations = [],
}) {
  const { zoomIn, zoomOut, fitView, zoomTo } = useReactFlow();
  const [isLocked, setIsLocked] = useState(false);
  const [isFocusMode, setIsFocusMode] = useState(false);
  const [focusedNodeId, setFocusedNodeId] = useState(null);

  // Animation playback state: 0 = ready/baseline, 1 = changed, 2 = direct, 3 = transitive, 4 = APIs, 5 = full evidence
  const [animStep, setAnimStep] = useState(5);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isAnimatedMode, setIsAnimatedMode] = useState(false);
  const playTimerRef = useRef(null);

  const effectiveAnalyzedId = analyzedServiceId || selectedServiceId;

  // Active target for focus inspection
  const activeFocusId = isFocusMode ? (focusedNodeId || effectiveAnalyzedId) : null;

  // Identify direct neighbors for focus mode
  const { focusNeighbors } = useMemo(() => {
    const neighbors = new Set();
    if (!activeFocusId) return { focusNeighbors: neighbors };

    neighbors.add(activeFocusId);
    for (const dep of dependencies) {
      const src = dep.source || dep.sourceServiceName;
      const tgt = dep.target || dep.targetServiceName;
      if (src === activeFocusId) neighbors.add(tgt);
      if (tgt === activeFocusId) neighbors.add(src);
    }
    return { focusNeighbors: neighbors };
  }, [dependencies, activeFocusId]);

  // Sets of affected services from impactResult
  const directlyChanged = useMemo(
    () => new Set(impactResult?.directlyChangedServices || (effectiveAnalyzedId ? [effectiveAnalyzedId] : [])),
    [impactResult, effectiveAnalyzedId]
  );
  const directAffected = useMemo(
    () => new Set(impactResult?.directAffectedServices || impactResult?.directlyImpactedServices || []),
    [impactResult]
  );
  const transitiveAffected = useMemo(
    () => new Set(impactResult?.transitiveAffectedServices || impactResult?.transitivelyImpactedServices || []),
    [impactResult]
  );

  // Correlate impacted APIs per service
  const serviceApisMap = useMemo(() => {
    const map = new Map();
    const apis = impactResult?.impactedApis || [];
    for (const api of apis) {
      const comp = api.controllerName || api.component;
      if (comp) {
        map.set(comp, (map.get(comp) || 0) + 1);
      }
    }
    return map;
  }, [impactResult?.impactedApis]);

  // Set of edge keys that participate in an impact path
  const { directEdgeKeys, transitiveEdgeKeys } = useMemo(() => {
    const directKeys = new Set();
    const transitiveKeys = new Set();

    if (!impactResult?.impactPaths) {
      return { directEdgeKeys: directKeys, transitiveEdgeKeys: transitiveKeys };
    }

    for (const item of impactResult.impactPaths) {
      const p = item.path || [];
      for (let i = 0; i < p.length - 1; i++) {
        const key = `${p[i]}->${p[i + 1]}`;
        if (directlyChanged.has(p[i + 1])) {
          directKeys.add(key);
        } else {
          transitiveKeys.add(key);
        }
      }
    }
    return { directEdgeKeys: directKeys, transitiveEdgeKeys: transitiveKeys };
  }, [impactResult, directlyChanged]);

  // Calculate intelligent tiered layout positions
  const computedPositions = useMemo(() => {
    const positions = new Map();

    // Check if services match predefined demo positions
    const allHavePredefined = services.every(
      (s) => Boolean(NODE_POSITIONS[s.id] || NODE_POSITIONS[s.label])
    );

    if (allHavePredefined) {
      for (const s of services) {
        positions.set(s.id, NODE_POSITIONS[s.id] || NODE_POSITIONS[s.label]);
      }
      return positions;
    }

    // Group services by architectural tier layer
    const layerBuckets = new Map();
    for (const s of services) {
      const layer = getTierLayer(s.tier);
      if (!layerBuckets.has(layer)) {
        layerBuckets.set(layer, []);
      }
      layerBuckets.get(layer).push(s);
    }

    // Assign spaced, centered coordinates across layers
    for (const [layer, svcsInLayer] of layerBuckets.entries()) {
      const count = svcsInLayer.length;
      const horizontalSpacing = Math.max(300, Math.min(360, 1100 / Math.max(1, count)));
      const startX = 450 - ((count - 1) * horizontalSpacing) / 2;
      const y = 50 + layer * 170;

      svcsInLayer.forEach((s, idx) => {
        // If demo node coordinate exists, use it; otherwise use the tiered coordinate
        const fallbackPos = NODE_POSITIONS[s.id] || NODE_POSITIONS[s.label];
        if (fallbackPos) {
          positions.set(s.id, fallbackPos);
        } else {
          positions.set(s.id, {
            x: Math.round(startX + idx * horizontalSpacing),
            y: Math.round(y),
          });
        }
      });
    }

    return positions;
  }, [services]);

  // Build React Flow nodes with animation steps and focus filtering
  const nodes = useMemo(() => {
    return services.map((svc) => {
      let status = 'unaffected';
      const isChanged = directlyChanged.has(svc.id) || directlyChanged.has(svc.label);
      const isDirect = directAffected.has(svc.id) || directAffected.has(svc.label);
      const isTransitive = transitiveAffected.has(svc.id) || transitiveAffected.has(svc.label);

      // In animated mode, reveal statuses progressively according to animStep
      if (isAnimatedMode) {
        if (animStep >= 1 && isChanged) {
          status = 'changed';
        } else if (animStep >= 2 && isDirect) {
          status = 'direct';
        } else if (animStep >= 3 && isTransitive) {
          status = 'transitive';
        } else if (svc.id === proposedServiceId) {
          status = 'proposed';
        }
      } else {
        if (isChanged) {
          status = 'changed';
        } else if (isDirect) {
          status = 'direct';
        } else if (isTransitive) {
          status = 'transitive';
        } else if (svc.id === proposedServiceId) {
          status = 'proposed';
        }
      }

      const position = computedPositions.get(svc.id) || { x: 100, y: 100 };
      const isDimmed = Boolean(isFocusMode && activeFocusId && !focusNeighbors.has(svc.id));
      const endpointsCount = (isAnimatedMode && animStep < 4) ? 0 : (serviceApisMap.get(svc.id) || serviceApisMap.get(svc.label) || 0);

      return {
        id: svc.id,
        type: 'serviceNode',
        position,
        data: {
          id: svc.id,
          label: svc.label || svc.name || svc.id,
          type: svc.type || 'Core Domain',
          tier: svc.tier || 'Domain Tier',
          icon: svc.icon || 'database',
          status,
          isDimmed,
          endpointsCount,
        },
        style: {
          opacity: isDimmed ? 0.22 : 1,
          transition: 'opacity 0.25s ease, transform 0.25s ease',
        },
      };
    });
  }, [
    services,
    directlyChanged,
    directAffected,
    transitiveAffected,
    proposedServiceId,
    computedPositions,
    isAnimatedMode,
    animStep,
    isFocusMode,
    activeFocusId,
    focusNeighbors,
    serviceApisMap,
  ]);

  // Build React Flow edges with animated highlighting
  const edges = useMemo(() => {
    const visibleIds = new Set(services.map((service) => service.id));
    return dependencies
      .filter((dep) => visibleIds.has(dep.source || dep.sourceServiceName) && visibleIds.has(dep.target || dep.targetServiceName))
      .map((dep, index) => {
        const source = dep.source || dep.sourceServiceName;
        const target = dep.target || dep.targetServiceName;
        const edgeKey = `${source}->${target}`;

        const isDirectEdge = directEdgeKeys.has(edgeKey);
        const isTransitiveEdge = transitiveEdgeKeys.has(edgeKey);

        let isHighlighted = false;
        if (isAnimatedMode) {
          if (animStep >= 2 && isDirectEdge) isHighlighted = true;
          if (animStep >= 3 && isTransitiveEdge) isHighlighted = true;
        } else {
          isHighlighted = isDirectEdge || isTransitiveEdge;
        }

        let edgeColor = '#94a3b8'; // Slate-400
        let strokeWidth = 1.5;
        let animated = false;

        if (isHighlighted) {
          if (isDirectEdge) {
            edgeColor = '#ef4444'; // Red-500 direct impact
            strokeWidth = 2.6;
            animated = isAnimatedMode;
          } else if (isTransitiveEdge) {
            edgeColor = '#8b5cf6'; // Purple-500 transitive impact
            strokeWidth = 2.4;
            animated = isAnimatedMode;
          }
        } else if (effectiveAnalyzedId && (directlyChanged.size > 0 || directAffected.size > 0)) {
          edgeColor = '#cbd5e1'; // Slate-300 dimmed
          strokeWidth = 1.3;
        }

        // Focus mode edge dimming
        const isEdgeConnectedToFocus = isFocusMode && activeFocusId
          ? (source === activeFocusId || target === activeFocusId)
          : true;
        const edgeOpacity = (!isEdgeConnectedToFocus) ? 0.15 : (isHighlighted ? 1 : 0.65);

        // Custom routing handles
        let sourceHandle = 'bottom';
        let targetHandle = 'top';

        if (source === 'Order Service' && target === 'Payment Service') {
          sourceHandle = 'right';
          targetHandle = 'left';
        } else if (source === 'Notification Service' && target === 'Payment Service') {
          sourceHandle = 'right';
          targetHandle = 'bottom-target';
        }

        return {
          id: `e-${source}-${target}-${index}`,
          source,
          target,
          sourceHandle,
          targetHandle,
          animated,
          style: {
            stroke: edgeColor,
            strokeWidth,
            opacity: edgeOpacity,
            transition: 'stroke 0.3s, stroke-width 0.3s, opacity 0.25s',
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 16,
            height: 16,
            color: edgeColor,
          },
        };
      });
  }, [
    dependencies,
    services,
    directEdgeKeys,
    transitiveEdgeKeys,
    isAnimatedMode,
    animStep,
    effectiveAnalyzedId,
    directlyChanged,
    directAffected,
    isFocusMode,
    activeFocusId,
  ]);

  // Node Click: notify parent and set focus candidate
  const handleNodeClick = useCallback(
    (_, node) => {
      setFocusedNodeId(node.id);
      if (onNodeClick) {
        onNodeClick(node.id);
      }
    },
    [onNodeClick]
  );

  // Viewport Navigation Controls
  const handleZoomIn = useCallback(() => zoomIn({ duration: 250 }), [zoomIn]);
  const handleZoomOut = useCallback(() => zoomOut({ duration: 250 }), [zoomOut]);
  const handleFitView = useCallback(() => fitView({ padding: 0.18, duration: 400 }), [fitView]);
  const handleResetScale = useCallback(() => zoomTo(1.0, { duration: 300 }), [zoomTo]);

  // Fit view once on project/dataset change only
  const lastDatasetRef = useRef(datasetKey);
  useEffect(() => {
    if (datasetKey !== lastDatasetRef.current) {
      lastDatasetRef.current = datasetKey;
      const timer = setTimeout(() => {
        fitView({ padding: 0.18, duration: 400 });
      }, 80);
      return () => clearTimeout(timer);
    }
  }, [datasetKey, fitView]);

  // Focus Mode toggle
  const handleToggleFocus = useCallback(() => {
    setIsFocusMode((prev) => !prev);
  }, []);

  // Animated Playback Control
  const handleStartAnimation = useCallback(() => {
    setIsAnimatedMode(true);
    setAnimStep(1);
    setIsPlaying(true);
  }, []);

  const handlePauseAnimation = useCallback(() => {
    setIsPlaying(false);
  }, []);

  const handleResumeAnimation = useCallback(() => {
    setIsAnimatedMode(true);
    setIsPlaying(true);
  }, []);

  const handleStepForward = useCallback(() => {
    setIsAnimatedMode(true);
    setIsPlaying(false);
    setAnimStep((prev) => Math.min(5, prev + 1));
  }, []);

  const handleResetAnimation = useCallback(() => {
    setIsPlaying(false);
    setIsAnimatedMode(false);
    setAnimStep(0);
  }, []);

  // Step advancement timer
  useEffect(() => {
    if (isPlaying && isAnimatedMode) {
      playTimerRef.current = setInterval(() => {
        setAnimStep((prev) => {
          if (prev >= 5) {
            setIsPlaying(false);
            return 5;
          }
          return prev + 1;
        });
      }, 1300);
    } else {
      clearInterval(playTimerRef.current);
    }
    return () => clearInterval(playTimerRef.current);
  }, [isPlaying, isAnimatedMode]);

  const stepDescriptions = [
    'Baseline Architecture: No active changes simulated',
    'Step 1 of 5: Highlight Root Changed Components',
    'Step 2 of 5: Reveal Direct Callers & Dependencies',
    'Step 3 of 5: Propagate Transitive Blast Radius',
    'Step 4 of 5: Correlate Impacted API Endpoints',
    'Step 5 of 5: Complete Impact Trace & Evidence Verified',
  ];

  return (
    <div className="graph-view-card">
      {/* Graph Header with Navigation and Mode Controls */}
      <div className="graph-view-header">
        <div className="graph-title-group">
          <div className="graph-title-icon">
            <IconImpact size={18} className="text-primary" />
          </div>
          <div>
            <h3 className="section-title">Architecture Dependency Graph</h3>
            <p className="convention-caption">
              Directed edges (A &rarr; B) denote that component A depends on B. Select any node to inspect evidence or simulate blast radius.
            </p>
          </div>
        </div>

        {/* Animation Playback Toolbar */}
        <div className="animated-impact-controls" role="toolbar" aria-label="Change impact animation controls">
          {!isPlaying ? (
            <button
              type="button"
              className="anim-ctrl-btn primary"
              onClick={isAnimatedMode && animStep > 0 && animStep < 5 ? handleResumeAnimation : handleStartAnimation}
              title="Play progressive change impact animation"
            >
              <IconPlay size={14} />
              <span>{isAnimatedMode && animStep > 0 && animStep < 5 ? 'Resume' : 'Play Impact'}</span>
            </button>
          ) : (
            <button
              type="button"
              className="anim-ctrl-btn warning"
              onClick={handlePauseAnimation}
              title="Pause animation"
            >
              <IconPause size={14} />
              <span>Pause</span>
            </button>
          )}

          <button
            type="button"
            className="anim-ctrl-btn"
            onClick={handleStepForward}
            disabled={animStep >= 5 && isAnimatedMode}
            title="Step forward to next impact phase"
          >
            <IconChevronRight size={14} />
            <span>Step</span>
          </button>

          <button
            type="button"
            className="anim-ctrl-btn"
            onClick={handleResetAnimation}
            title="Reset to full static architecture view"
          >
            <IconRefresh size={14} />
            <span>Reset</span>
          </button>
        </div>
      </div>

      {/* Animation Progress Banner */}
      {isAnimatedMode && (
        <div className="animation-progress-bar-wrap">
          <div className="anim-progress-track">
            <div
              className="anim-progress-fill"
              style={{ width: `${(animStep / 5) * 100}%` }}
            />
          </div>
          <div className="anim-step-label-row">
            <span className="anim-step-desc">
              <strong>Phase {animStep}:</strong> {stepDescriptions[animStep]}
              {animStep === 5 && impactExplanations?.length > 0 && (
                <span className="anim-evidence-badge">
                  {' '}&bull; {impactExplanations.length} source evidence path{impactExplanations.length > 1 ? 's' : ''} available (click any node to inspect)
                </span>
              )}
            </span>
            <span className="anim-step-pct">{animStep} / 5</span>
          </div>
        </div>
      )}

      {/* Focus Mode Status Pill */}
      {isFocusMode && activeFocusId && (
        <div className="focus-mode-indicator-bar">
          <div className="focus-bar-left">
            <IconSearch size={15} />
            <span>
              <strong>Focus Mode Active:</strong> Isolating <code>{activeFocusId}</code> and {focusNeighbors.size - 1} connected component(s).
            </span>
          </div>
          <button
            type="button"
            className="btn-exit-focus"
            onClick={() => setIsFocusMode(false)}
          >
            Exit Focus Mode
          </button>
        </div>
      )}

      {/* React Flow Canvas */}
      <div
        className="react-flow-wrapper"
        style={{ width: '100%', height: '620px', minHeight: '520px', position: 'relative' }}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodeClick={handleNodeClick}
          fitView
          fitViewOptions={{ padding: 0.18 }}
          minZoom={0.3}
          maxZoom={2.2}
          panOnDrag={!isLocked}
          zoomOnScroll={!isLocked}
          preventScrolling={true}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="#cbd5e1" gap={20} size={1.2} />
        </ReactFlow>

        {/* Floating Zoom & Pan Controls on Bottom Left */}
        <div className="graph-floating-controls-left" role="toolbar" aria-label="Graph controls">
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={handleZoomIn}
            title="Zoom In (+)"
            aria-label="Zoom In"
          >
            <IconPlus size={16} />
          </button>
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={handleZoomOut}
            title="Zoom Out (-)"
            aria-label="Zoom Out"
          >
            <IconMinus size={16} />
          </button>
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={handleFitView}
            title="Fit to Center"
            aria-label="Fit to Center"
          >
            <IconFitView size={16} />
          </button>
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={handleResetScale}
            title="Reset Scale to 100%"
            aria-label="Reset Scale 100%"
          >
            <span style={{ fontSize: '11px', fontWeight: 'bold' }}>1:1</span>
          </button>
          <button
            type="button"
            className={`graph-ctrl-btn ${isFocusMode ? 'active' : ''}`}
            onClick={handleToggleFocus}
            title={isFocusMode ? 'Exit Component Focus Mode' : 'Focus Mode on Selected Component'}
            aria-label="Toggle Focus Mode"
          >
            <IconSearch size={15} />
          </button>
          <button
            type="button"
            className={`graph-ctrl-btn ${isLocked ? 'active' : ''}`}
            onClick={() => setIsLocked(!isLocked)}
            title={isLocked ? 'Unlock Canvas Navigation' : 'Lock Canvas Navigation'}
            aria-label="Toggle Canvas Lock"
          >
            <IconLockControls size={15} />
          </button>
        </div>

        {/* Graph Legend on Bottom Right */}
        <div className="graph-floating-legend" aria-label="Graph legend">
          <span className="legend-item">
            <span className="legend-dot dot-changed" />
            <span className="legend-text">Changed Root</span>
          </span>
          <span className="legend-item">
            <span className="legend-dot dot-direct" />
            <span className="legend-text">Direct Dependent</span>
          </span>
          <span className="legend-item">
            <span className="legend-dot dot-transitive" />
            <span className="legend-text">Transitive Caller</span>
          </span>
          <span className="legend-item">
            <span className="legend-dot dot-unaffected" />
            <span className="legend-text">Unaffected</span>
          </span>
        </div>
      </div>
    </div>
  );
}

export function GraphView(props) {
  return (
    <ReactFlowProvider>
      <GraphInner {...props} />
    </ReactFlowProvider>
  );
}
