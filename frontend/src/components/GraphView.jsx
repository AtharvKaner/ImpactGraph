import React, { useMemo, useState, useCallback } from 'react';
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
} from './Icons';

const nodeTypes = {
  serviceNode: ServiceNode,
};

function GraphInner({
  services,
  dependencies,
  proposedServiceId = '',
  analyzedServiceId = '',
  selectedServiceId = '',
  impactResult,
  onNodeClick,
}) {
  const { zoomIn, zoomOut, fitView } = useReactFlow();
  const [isLocked, setIsLocked] = useState(false);
  const effectiveAnalyzedId = analyzedServiceId || selectedServiceId;

  const directAffected = useMemo(
    () => new Set(impactResult?.directAffectedServices || []),
    [impactResult]
  );
  const transitiveAffected = useMemo(
    () => new Set(impactResult?.transitiveAffectedServices || []),
    [impactResult]
  );

  // Set of edge keys that participate in an impact path
  const { directEdgeKeys, transitiveEdgeKeys } = useMemo(() => {
    const directKeys = new Set();
    const transitiveKeys = new Set();

    if (!impactResult?.impactPaths || !effectiveAnalyzedId) {
      return { directEdgeKeys: directKeys, transitiveEdgeKeys: transitiveKeys };
    }

    for (const item of impactResult.impactPaths) {
      const p = item.path || [];
      for (let i = 0; i < p.length - 1; i++) {
        const key = `${p[i]}->${p[i + 1]}`;
        // If the target in this hop is the changed service, this hop is direct
        if (p[i + 1] === effectiveAnalyzedId) {
          directKeys.add(key);
        } else {
          transitiveKeys.add(key);
        }
      }
    }
    return { directEdgeKeys: directKeys, transitiveEdgeKeys: transitiveKeys };
  }, [impactResult, effectiveAnalyzedId]);

  // Build React Flow nodes
  const nodes = useMemo(() => {
    return services.map((svc, index) => {
      let status = 'unaffected';
      const directlyChanged = new Set(impactResult?.directlyChangedServices || []);
      if (svc.id === effectiveAnalyzedId || directlyChanged.has(svc.id) || directlyChanged.has(svc.label)) {
        status = 'changed';
      } else if (directAffected.has(svc.id) || directAffected.has(svc.label)) {
        status = 'direct';
      } else if (transitiveAffected.has(svc.id) || transitiveAffected.has(svc.label)) {
        status = 'transitive';
      } else if (svc.id === proposedServiceId) {
        status = 'proposed';
      }

      // Check predefined positions first (matches standard demo names)
      let position = NODE_POSITIONS[svc.id] || NODE_POSITIONS[svc.label];
      if (!position) {
        // Deterministic grid layout for persisted services without predefined coordinates
        const col = index % 3;
        const row = Math.floor(index / 3);
        position = { x: 80 + col * 240, y: 80 + row * 160 };
      }

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
        },
      };
    });
  }, [services, effectiveAnalyzedId, proposedServiceId, directAffected, transitiveAffected, impactResult?.directlyChangedServices]);

  // Build React Flow edges
  // Convention: source -> target means source depends on target
  const edges = useMemo(() => {
    const visibleIds = new Set(services.map((service) => service.id));
    return dependencies.filter((dep) => visibleIds.has(dep.source || dep.sourceServiceName) && visibleIds.has(dep.target || dep.targetServiceName)).map((dep, index) => {
      const source = dep.source || dep.sourceServiceName;
      const target = dep.target || dep.targetServiceName;
      const edgeKey = `${source}->${target}`;
      const isDirectEdge = directEdgeKeys.has(edgeKey);
      const isTransitiveEdge = transitiveEdgeKeys.has(edgeKey);
      const isImpactEdge = isDirectEdge || isTransitiveEdge;

      let edgeColor = '#94a3b8'; // Slate-400
      let strokeWidth = 1.5;
      let animated = false;

      if (isDirectEdge) {
        edgeColor = '#ef4444'; // Red-500
        strokeWidth = 2.2;
        animated = false;
      } else if (isTransitiveEdge) {
        edgeColor = '#8b5cf6'; // Purple-500
        strokeWidth = 2.2;
        animated = false;
      } else if (effectiveAnalyzedId) {
        edgeColor = '#cbd5e1'; // Slate-300
        strokeWidth = 1.4;
      }

      // Handle custom attachment points for cleaner routing
      let sourceHandle = 'bottom';
      let targetHandle = 'top';

      if (source === 'Order Service' && target === 'Payment Service') {
        sourceHandle = 'right';
        targetHandle = 'left';
      } else if (source === 'Notification Service' && target === 'Payment Service') {
        // Circular back-edge from bottom row to upper row
        sourceHandle = 'right';
        targetHandle = 'bottom';
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
          opacity: effectiveAnalyzedId && !isImpactEdge ? 0.65 : 1,
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 15,
          height: 15,
          color: edgeColor,
        },
      };
    });
  }, [dependencies, services, directEdgeKeys, transitiveEdgeKeys, effectiveAnalyzedId]);

  const handleNodeClick = useCallback(
    (_, node) => {
      if (onNodeClick) {
        onNodeClick(node.id);
      }
    },
    [onNodeClick]
  );

  const handleFitView = useCallback(() => {
    fitView({ padding: 0.15, duration: 400 });
  }, [fitView]);

  return (
    <div className="graph-view-card">
      {/* Graph Header */}
      <div className="graph-view-header">
        <div className="graph-title-group">
          <div className="graph-title-icon">
            <IconImpact size={18} className="text-primary" />
          </div>
          <div>
            <h3 className="section-title">Dependency Graph</h3>
            <p className="convention-caption">
              A &rarr; B means service A depends on service B. Click any node to select it as the proposed change target.
            </p>
          </div>
        </div>

        {/* Compact Legend */}
        <div className="graph-legend">
          <span className="legend-item">
            <span className="legend-dot dot-changed" />
            <span className="legend-text">Target (Simulated Change)</span>
          </span>
          <span className="legend-item">
            <span className="legend-dot dot-direct" />
            <span className="legend-text">Direct Impact</span>
          </span>
          <span className="legend-item">
            <span className="legend-dot dot-transitive" />
            <span className="legend-text">Indirect Impact</span>
          </span>
          <span className="legend-item">
            <span className="legend-dot dot-unaffected" />
            <span className="legend-text">Unaffected</span>
          </span>
        </div>
      </div>

      {/* React Flow Canvas */}
      <div className="react-flow-wrapper" style={{ width: '100%', height: '580px', minHeight: '500px', position: 'relative' }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodeClick={handleNodeClick}
          fitView
          fitViewOptions={{ padding: 0.15 }}
          minZoom={0.4}
          maxZoom={1.8}
          panOnDrag={!isLocked}
          zoomOnScroll={!isLocked}
          preventScrolling={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="#cbd5e1" gap={18} size={1.2} />
        </ReactFlow>

        {/* Floating Zoom & Pan Controls on Bottom Left */}
        <div className="graph-floating-controls-left" role="toolbar" aria-label="Graph controls">
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={() => zoomIn({ duration: 300 })}
            title="Zoom In"
            aria-label="Zoom In"
          >
            <IconPlus size={15} />
          </button>
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={() => zoomOut({ duration: 300 })}
            title="Zoom Out"
            aria-label="Zoom Out"
          >
            <IconMinus size={15} />
          </button>
          <button
            type="button"
            className="graph-ctrl-btn"
            onClick={handleFitView}
            title="Fit to Center"
            aria-label="Fit to Center"
          >
            <IconFitView size={15} />
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

        {/* Floating Helper Notice at Bottom Right */}
        <div className="graph-helper-notice">
          <span>Click any node to select it as the proposed change target</span>
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
