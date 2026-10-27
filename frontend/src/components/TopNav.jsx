import React, { useState, useRef, useEffect } from 'react';
import {
  IconLogo,
  IconSearch,
  IconRefresh,
  IconLogOut,
  ServiceIcon,
} from './Icons';

export function TopNav({
  services = [],
  selectedServiceId,
  onSelectService,
  healthData,
  backendLoading,
  onRefresh,
  onOpenBackendDetails,
  isDemoFallback = false,
  user = null,
  onLogout = null,
  onOpenDemoProject = null,
  isDemoActive = false,
  onOpenImportModal = null,
}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const searchContainerRef = useRef(null);

  // Filter services based on search query
  const filteredServices = services.filter((svc) =>
    svc.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
    svc.tier.toLowerCase().includes(searchQuery.toLowerCase()) ||
    svc.type.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(e) {
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target)) {
        setIsSearchOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelectService = (id) => {
    onSelectService(id);
    setSearchQuery('');
    setIsSearchOpen(false);
  };

  return (
    <header className="top-nav-bar">
      {/* Brand & Logo */}
      <div className="top-nav-brand">
        <IconLogo size={34} />
        <div className="brand-text-col">
          <span className="brand-title">ImpactGraph</span>
          <span className="brand-subtitle">Software Change Impact Analysis</span>
        </div>
      </div>

      {/* Global Search Bar with Autocomplete Dropdown */}
      <div className="top-nav-search-wrapper" ref={searchContainerRef}>
        <div className="search-input-box">
          <IconSearch size={15} className="search-icon" />
          <input
            type="text"
            className="search-input-field"
            placeholder="Search services..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setIsSearchOpen(true);
            }}
            onFocus={() => setIsSearchOpen(true)}
            aria-label="Search services"
          />
        </div>

        {isSearchOpen && searchQuery.trim().length > 0 && (
          <div className="search-results-dropdown">
            {filteredServices.length === 0 ? (
              <div className="search-empty-result">No services found</div>
            ) : (
              filteredServices.map((svc) => (
                <button
                  type="button"
                  key={svc.id}
                  className={`search-result-item ${svc.id === selectedServiceId ? 'selected' : ''}`}
                  onClick={() => handleSelectService(svc.id)}
                >
                  <div className="search-result-icon">
                    <ServiceIcon name={svc.icon} size={14} />
                  </div>
                  <div className="search-result-info">
                    <span className="search-result-name">{svc.label}</span>
                    <span className="search-result-type">{svc.tier} • {svc.type}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        )}
      </div>

      {/* Right Controls: Backend Status, Refresh, User Profile */}
      <div className="top-nav-right">
        {/* Import Real Repository */}
        {onOpenImportModal && (
          <button
            type="button"
            className="nav-import-btn"
            onClick={onOpenImportModal}
            title="Import real GitHub repository or local workspace for architecture analysis"
          >
            <span className="nav-import-icon">📦</span>
            <span className="nav-import-text">Import Repo</span>
          </button>
        )}

        {/* Quick Demo Project Switcher */}
        {onOpenDemoProject && (
          <button
            type="button"
            className={`nav-demo-btn ${isDemoActive ? 'demo-active' : ''}`}
            onClick={onOpenDemoProject}
            title={isDemoActive ? 'Demo project currently active' : 'Open ready-to-explore multi-service demo project'}
          >
            <span className="nav-demo-icon">⚡</span>
            <span className="nav-demo-text">{isDemoActive ? 'Demo Active' : 'Open Demo'}</span>
          </button>
        )}

        {/* Backend Online / Offline Pill */}
        <button
          type="button"
          className="backend-status-pill"
          onClick={onOpenBackendDetails}
          title="Click to view backend connection details"
        >
          <span
            className={`backend-status-dot ${
              backendLoading ? 'dot-loading' : healthData ? 'dot-online' : 'dot-offline'
            }`}
          />
          <span className="backend-status-label">
            {backendLoading
              ? 'Checking Engine...'
              : healthData
              ? 'Analysis Engine Online'
              : isDemoFallback
              ? 'Engine Offline (Demo Mode)'
              : 'Engine Offline'}
          </span>
        </button>

        {/* Refresh Button */}
        <button
          type="button"
          className={`nav-icon-btn ${backendLoading ? 'refreshing' : ''}`}
          onClick={onRefresh}
          disabled={backendLoading}
          title="Refresh Backend Health"
          aria-label="Refresh Backend Status"
        >
          <IconRefresh size={16} />
        </button>

        {/* User Profile Avatar & Logout */}
        {user ? (
          <div className="top-nav-user-group">
            <div
              className="user-profile-avatar"
              title={`${user.displayName || user.email} (${user.role || 'USER'})`}
            >
              <span>
                {(user.displayName?.[0] || user.email?.[0] || 'U').toUpperCase()}
              </span>
            </div>
            <div className="user-profile-meta">
              <span className="user-profile-name" title={user.email}>
                {user.displayName || user.email}
              </span>
              <span className="user-profile-role">{user.role || 'USER'}</span>
            </div>
            {onLogout && (
              <button
                type="button"
                className="nav-logout-btn"
                onClick={onLogout}
                title="Sign out of ImpactGraph"
                aria-label="Sign out"
              >
                <IconLogOut size={13} />
                <span className="logout-text">Logout</span>
              </button>
            )}
          </div>
        ) : (
          <div className="user-profile-avatar" title="Guest">
            <span>G</span>
          </div>
        )}
      </div>
    </header>
  );
}
