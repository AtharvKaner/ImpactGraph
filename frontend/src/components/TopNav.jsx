import React, { useState, useRef, useEffect } from 'react';
import {
  IconLogo,
  IconSearch,
  IconRefresh,
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
              ? 'Checking Backend...'
              : healthData
              ? 'Backend Online (8080)'
              : isDemoFallback
              ? 'Backend Offline (Demo Fallback)'
              : 'Backend Offline'}
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

        {/* User Profile Avatar */}
        <div className="user-profile-avatar" title="Atharv (Dev)">
          <span>A</span>
        </div>
      </div>
    </header>
  );
}
