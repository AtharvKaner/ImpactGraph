import React, { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import { ServiceIcon } from './Icons';

function ServiceNodeComponent({ data, selected }) {
  const { label, type, icon, status } = data;

  // status can be 'changed', 'direct', 'transitive', or 'unaffected'
  const statusClass = status ? `node-${status}` : 'node-unaffected';
  const isSelected = selected ? 'node-selected' : '';

  return (
    <div className={`service-node-card ${statusClass} ${isSelected}`} title={`${label} (${type})`}>
      {/* Handles for incoming (target) edges */}
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        className="rf-handle rf-handle-top"
      />
      <Handle
        type="target"
        position={Position.Left}
        id="left"
        className="rf-handle rf-handle-left"
      />
      <Handle
        type="target"
        position={Position.Bottom}
        id="bottom-target"
        className="rf-handle rf-handle-bottom"
      />
      <Handle
        type="target"
        position={Position.Right}
        id="right-target"
        className="rf-handle rf-handle-right"
      />

      <div className="service-node-icon-wrapper">
        <ServiceIcon name={icon} size={18} className="service-icon-svg" />
      </div>

      <div className="service-node-content">
        <span className="service-node-label">{label}</span>
        <span className="service-node-tier">{data.tier || 'Domain Tier'}</span>
        <span className="service-node-type">{type}</span>
        {data.endpointsCount > 0 && (
          <span className="service-node-api-pill" title={`${data.endpointsCount} correlated API routes in blast radius`}>
            ⚡ {data.endpointsCount} API{data.endpointsCount > 1 ? 's' : ''}
          </span>
        )}
      </div>

      {/* Handles for outgoing (source) edges */}
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        className="rf-handle rf-handle-right"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="bottom"
        className="rf-handle rf-handle-bottom"
      />
      <Handle
        type="source"
        position={Position.Top}
        id="top-source"
        className="rf-handle rf-handle-top"
      />
      <Handle
        type="source"
        position={Position.Left}
        id="left-source"
        className="rf-handle rf-handle-left"
      />
    </div>
  );
}

export const ServiceNode = memo(ServiceNodeComponent);
