import React from 'react';
import {
  IconDashboard,
  IconServices,
  IconImpact,
  IconTopology,
  IconSettings,
  IconDatabase,
} from './Icons';

export function Sidebar({
  activeNav = 'dashboard',
  onSelectNav,
  onOpenSettings,
  onOpenServicesModal,
}) {
  const handleNavClick = (navKey) => {
    if (navKey === 'settings') {
      onOpenSettings();
    } else if (navKey === 'services' && onOpenServicesModal) {
      onOpenServicesModal();
    } else {
      onSelectNav(navKey);
    }
  };

  return (
    <aside className="app-sidebar" aria-label="Main Navigation">
      <nav className="sidebar-nav-list">
        <button
          type="button"
          className={`sidebar-nav-item ${activeNav === 'dashboard' ? 'active' : ''}`}
          onClick={() => handleNavClick('dashboard')}
          title="Dashboard Overview"
        >
          <IconDashboard size={18} className="sidebar-nav-icon" />
          <span className="sidebar-nav-label">Dashboard</span>
        </button>

        <button
          type="button"
          className={`sidebar-nav-item ${activeNav === 'services' ? 'active' : ''}`}
          onClick={() => handleNavClick('services')}
          title="Services Directory"
        >
          <IconServices size={18} className="sidebar-nav-icon" />
          <span className="sidebar-nav-label">Services</span>
        </button>

        <button
          type="button"
          className={`sidebar-nav-item ${activeNav === 'impact' ? 'active' : ''}`}
          onClick={() => handleNavClick('impact')}
          title="Change Impact Analysis"
        >
          <IconImpact size={18} className="sidebar-nav-icon" />
          <span className="sidebar-nav-label">Impact Analysis</span>
        </button>

        <button
          type="button"
          className={`sidebar-nav-item ${activeNav === 'topology' ? 'active' : ''}`}
          onClick={() => handleNavClick('topology')}
          title="Topology & Cycles"
        >
          <IconTopology size={18} className="sidebar-nav-icon" />
          <span className="sidebar-nav-label">Topology</span>
        </button>

        <button
          type="button"
          className={`sidebar-nav-item ${activeNav === 'settings' ? 'active' : ''}`}
          onClick={() => handleNavClick('settings')}
          title="Backend Connection & Settings"
        >
          <IconSettings size={18} className="sidebar-nav-icon" />
          <span className="sidebar-nav-label">Settings</span>
        </button>
      </nav>

      {/* Discreet Demo Dataset Indicator at Sidebar Bottom */}
      <div className="sidebar-demo-badge-card">
        <div className="demo-badge-header">
          <IconDatabase size={16} className="demo-badge-icon" />
          <span className="demo-badge-title">Demo Dataset</span>
        </div>
        <p className="demo-badge-desc">
          In-memory data (PostgreSQL integration pending Phase 2B)
        </p>
      </div>
    </aside>
  );
}
