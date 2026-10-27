import React, { useState, useMemo } from 'react';
import {
  IconSearch,
  IconClear,
  IconImpact,
  IconServices,
  ServiceIcon,
} from '../components/Icons';

export function ServicesView({
  services = [],
  dependencies = [],
  graphData,
  onSelectServiceAndStudio,
  selectedProjectId,
  currentPreset,
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [tierFilter, setTierFilter] = useState('ALL');

  const tiers = ['ALL', 'Client Tier', 'Application Tier', 'Domain Tier', 'Infrastructure Tier', 'Integration Tier'];

  // Calculate in/out dependencies for each service
  const serviceStats = useMemo(() => {
    const stats = new Map();
    for (const s of services) {
      stats.set(s.id, { incoming: 0, outgoing: 0, incomingNames: [], outgoingNames: [] });
    }

    for (const d of dependencies) {
      const source = d.source || d.sourceServiceName;
      const target = d.target || d.targetServiceName;

      if (stats.has(source)) {
        const item = stats.get(source);
        item.outgoing += 1;
        item.outgoingNames.push(target);
      }
      if (stats.has(target)) {
        const item = stats.get(target);
        item.incoming += 1;
        item.incomingNames.push(source);
      }
    }
    return stats;
  }, [services, dependencies]);

  const filteredServices = useMemo(() => {
    return services.filter((s) => {
      const matchesSearch = !searchTerm.trim() ||
        (s.label || s.name || s.id).toLowerCase().includes(searchTerm.toLowerCase()) ||
        (s.type || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
        (s.description || '').toLowerCase().includes(searchTerm.toLowerCase());
      const matchesTier = tierFilter === 'ALL' || s.tier === tierFilter;
      return matchesSearch && matchesTier;
    });
  }, [services, searchTerm, tierFilter]);

  const cycleNodeSet = useMemo(() => {
    const set = new Set();
    const cycles = graphData?.cycles || graphData?.cycleDetails || [];
    for (const c of cycles) {
      const cycleNodes = c.cycleNodes || [];
      for (const n of cycleNodes) set.add(n);
    }
    return set;
  }, [graphData]);

  return (
    <div className="view-container services-view">
      <div className="view-header-row">
        <div>
          <h2 className="view-title">Microservices & API Directory</h2>
          <p className="view-subtitle">
            Comprehensive catalog of registered components, dependency connectivity, and topology tier distribution for{' '}
            <strong>{selectedProjectId ? (graphData?.name || `Repository Project #${selectedProjectId}`) : (graphData?.name || currentPreset)}</strong>
          </p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="table-controls-bar">
        <div className="filter-search-box">
          <IconSearch size={14} className="filter-search-icon" />
          <input
            type="text"
            className="filter-search-input"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by service name, tier, or purpose..."
          />
          {searchTerm && (
            <button
              type="button"
              className="filter-search-clear"
              onClick={() => setSearchTerm('')}
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
              className={`tier-chip ${tierFilter === t ? 'active' : ''}`}
              onClick={() => setTierFilter(t)}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      {/* Services Cards / Grid View */}
      <div className="services-catalog-grid">
        {filteredServices.map((svc) => {
          const stats = serviceStats.get(svc.id) || { incoming: 0, outgoing: 0 };
          const inCycle = cycleNodeSet.has(svc.id) || cycleNodeSet.has(svc.label);

          return (
            <div key={svc.id} className="service-catalog-card">
              <div className="catalog-card-head">
                <div className="catalog-card-icon-wrap">
                  <ServiceIcon name={svc.icon || 'database'} size={20} />
                </div>
                <div className="catalog-card-title-group">
                  <h4 className="catalog-service-name">{svc.label || svc.name || svc.id}</h4>
                  <span className="catalog-service-tier">{svc.tier || 'Domain Tier'}</span>
                </div>
                {inCycle && (
                  <span className="cycle-badge-pill" title="Participates in circular feedback loop">
                    Cycle Member
                  </span>
                )}
              </div>

              <p className="catalog-service-desc">
                {svc.description || 'Core service orchestrating business domain operations and dependencies.'}
              </p>

              <div className="catalog-card-meta-metrics">
                <div className="meta-metric">
                  <span className="metric-label">Dependents (In):</span>
                  <span className="metric-value">{stats.incoming}</span>
                </div>
                <div className="meta-metric">
                  <span className="metric-label">Dependencies (Out):</span>
                  <span className="metric-value">{stats.outgoing}</span>
                </div>
                <div className="meta-metric">
                  <span className="metric-label">Type:</span>
                  <span className="metric-value-type">{svc.type || 'Core Domain'}</span>
                </div>
              </div>

              <div className="catalog-card-actions">
                <button
                  type="button"
                  className="simulate-change-btn"
                  onClick={() => onSelectServiceAndStudio(svc.id)}
                  title={`Simulate change blast radius on ${svc.label || svc.id}`}
                >
                  <IconImpact size={14} />
                  <span>Simulate Change &rarr;</span>
                </button>
              </div>
            </div>
          );
        })}

        {filteredServices.length === 0 && (
          <div className="empty-catalog-state">
            <IconServices size={32} className="text-muted" />
            <h4>No microservices matched your criteria</h4>
            <p>Try resetting filters or adjusting search keywords.</p>
          </div>
        )}
      </div>
    </div>
  );
}
