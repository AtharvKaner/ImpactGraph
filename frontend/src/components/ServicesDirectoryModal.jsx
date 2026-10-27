import React from 'react';
import {
  IconServices,
  IconClear,
  ServiceIcon,
} from './Icons';

export function ServicesDirectoryModal({
  isOpen,
  onClose,
  services = [],
  selectedServiceId,
  onSelectService,
}) {
  if (!isOpen) return null;

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-window modal-wide" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="modal-title-wrap">
            <div className="modal-title-icon">
              <IconServices size={18} className="text-primary" />
            </div>
            <div>
              <h3 className="modal-heading">System Services Directory</h3>
              <p className="modal-subheading">Select any service to evaluate change impact</p>
            </div>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close modal"
          >
            <IconClear size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div className="services-directory-grid">
            {services.map((svc) => {
              const isSelected = svc.id === selectedServiceId;
              return (
                <button
                  type="button"
                  key={svc.id}
                  className={`service-dir-card ${isSelected ? 'selected' : ''}`}
                  onClick={() => {
                    onSelectService(svc.id);
                    onClose();
                  }}
                >
                  <div className="dir-card-header">
                    <div className="dir-card-icon-box">
                      <ServiceIcon name={svc.icon} size={18} className={isSelected ? 'icon-amber' : ''} />
                    </div>
                    <span className="dir-card-tier">{svc.tier}</span>
                  </div>
                  <div className="dir-card-body">
                    <span className="dir-card-name">{svc.label}</span>
                    <span className="dir-card-type">{svc.type}</span>
                  </div>
                  <div className="dir-card-footer">
                    <span className="dir-select-action">
                      {isSelected ? 'Currently Selected' : 'Simulate Change \u2192'}
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        <div className="modal-footer">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
