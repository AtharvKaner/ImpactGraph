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
      {/* Handles for incoming and outgoing edges */}
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

      <div className="service-node-icon-wrapper">
        <ServiceIcon name={icon} size={18} className="service-icon-svg" />
      </div>

      <div className="service-node-content">
        <span className="service-node-label">{label}</span>
        <span className="service-node-type">{type}</span>
      </div>

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
    </div>
  );
}

export const ServiceNode = memo(ServiceNodeComponent);
