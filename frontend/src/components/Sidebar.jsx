import React from 'react';
import {
  IconDashboard,
  IconImpact,
  IconServices,
  IconTopology,
  IconCode,
  IconShieldAlert,
  IconHistory,
  IconFileText,
  IconCheckShield,
  IconSettings,
  IconDatabase,
} from './Icons';

export function Sidebar({
  activeNav = 'overview',
  onSelectNav,
  user = null,
}) {
  const isAdmin = user?.role === 'ADMIN';

  const navSections = [
    {
      title: 'Architecture & Impact',
      items: [
        { key: 'overview', label: 'Overview', icon: IconDashboard },
        { key: 'studio', label: 'Impact Studio', icon: IconImpact },
        { key: 'services', label: 'Services Directory', icon: IconServices },
        { key: 'topology', label: 'Topology & Health', icon: IconTopology },
      ],
    },
    {
      title: 'Security & Verification',
      items: [
        { key: 'apis', label: 'API Security', icon: IconCode },
        { key: 'findings', label: 'Security Findings', icon: IconShieldAlert },
        { key: 'history', label: 'Analysis History', icon: IconHistory },
      ],
    },
    {
      title: 'Governance & Operations',
      items: [
        {
          key: 'audit',
          label: 'Audit Explorer',
          icon: IconFileText,
          badge: isAdmin ? 'ADMIN' : 'USER',
          badgeClass: isAdmin ? 'badge-purple' : 'badge-neutral',
        },
        { key: 'transparency', label: 'Transparency', icon: IconCheckShield },
        { key: 'settings', label: 'Settings', icon: IconSettings },
      ],
    },
  ];

  return (
    <aside className="app-sidebar" aria-label="Main Navigation">
      <div className="sidebar-scrollable-content">
        {navSections.map((section, sIdx) => (
          <div key={sIdx} className="sidebar-section">
            <span className="sidebar-section-title">{section.title}</span>
            <nav className="sidebar-nav-list">
              {section.items.map((item) => {
                const IconComp = item.icon;
                const isActive = activeNav === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    className={`sidebar-nav-item ${isActive ? 'active' : ''}`}
                    onClick={() => onSelectNav(item.key)}
                    title={item.label}
                  >
                    <IconComp size={18} className="sidebar-nav-icon" />
                    <span className="sidebar-nav-label">{item.label}</span>
                    {item.badge && (
                      <span className={`sidebar-item-badge ${item.badgeClass}`}>
                        {item.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </nav>
          </div>
        ))}
      </div>

      {/* Dataset & Mode Indicator at Sidebar Bottom */}
      <div className="sidebar-footer-card">
        <div className="sidebar-footer-header">
          <IconDatabase size={15} className="demo-badge-icon" />
          <span className="demo-badge-title">Analysis Engine</span>
        </div>
        <p className="demo-badge-desc">
          Java 21 AST • Static Reachability • Security Rules
        </p>
      </div>
    </aside>
  );
}
