import { useState, useEffect, useCallback } from 'react';
import { DATASET_PRESETS } from './data/mockServicesData';
import {
  fetchServiceGraph,
  fetchProjects,
  fetchProjectGraph,
  analyzeChangeImpact,
  checkBackendHealth,
  getStoredUser,
  getStoredToken,
  logout,
  API_BASE_URL,
  fetchDemoProject,
  fetchSecurityFindings,
  fetchAnalysisHistory,
  fetchAuditEvents,
} from './services/impactService';
import { TopNav } from './components/TopNav';
import { Sidebar } from './components/Sidebar';
import { BackendStatusModal } from './components/BackendStatusModal';
import { ServicesDirectoryModal } from './components/ServicesDirectoryModal';
import ImportRepositoryModal from './components/ImportRepositoryModal';
import { LoginView } from './components/LoginView';

// Distinct Multi-View Screens
import { OverviewView } from './views/OverviewView';
import { ImpactStudioView } from './views/ImpactStudioView';
import { ServicesView } from './views/ServicesView';
import { TopologyView } from './views/TopologyView';
import { ApiSecurityView } from './views/ApiSecurityView';
import { SecurityFindingsView } from './views/SecurityFindingsView';
import { AnalysisHistoryView } from './views/AnalysisHistoryView';
import { AuditExplorerView } from './views/AuditExplorerView';
import { TransparencyView } from './views/TransparencyView';
import { SettingsView } from './views/SettingsView';

import './App.css';

const VALID_VIEWS = new Set([
  'overview',
  'studio',
  'services',
  'topology',
  'apis',
  'findings',
  'history',
  'audit',
  'transparency',
  'settings',
]);

function getInitialView() {
  if (typeof window !== 'undefined' && window.location.hash) {
    const raw = window.location.hash.replace(/^#\/?/, '').trim().toLowerCase();
    if (VALID_VIEWS.has(raw)) return raw;
  }
  return 'overview';
}

function App() {
  // Authentication session state restored from sessionStorage
  const [currentUser, setCurrentUser] = useState(() => getStoredUser());
  const [authToken, setAuthToken] = useState(() => getStoredToken());

  // Backend health status state
  const [healthData, setHealthData] = useState(null);
  const [backendLoading, setBackendLoading] = useState(true);
  const [backendError, setBackendError] = useState(null);
  const [lastChecked, setLastChecked] = useState(null);

  // Persisted projects state
  const [projects, setProjects] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState(() => {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      const stored = window.sessionStorage.getItem('impactgraph_selected_project_id');
      return stored ? Number(stored) : null;
    }
    return null;
  });

  // Security telemetry and audit history state
  const [findings, setFindings] = useState([]);
  const [historyRuns, setHistoryRuns] = useState([]);
  const [auditEvents, setAuditEvents] = useState([]);

  // Impact analysis dashboard state
  const [currentPreset, setCurrentPreset] = useState(DATASET_PRESETS.ECOMMERCE_DAG);
  const [graphData, setGraphData] = useState(null);

  // Proposed change target vs executed analysis target
  const [proposedServiceId, setProposedServiceId] = useState('Payment Service');
  const [analyzedServiceId, setAnalyzedServiceId] = useState(null);
  const [impactResult, setImpactResult] = useState(null);
  const [impactLoading, setImpactLoading] = useState(false);

  // Server error banner state
  const [apiError, setApiError] = useState(null);

  // Active Multi-View Navigation (overview, studio, services, topology, apis, findings, history, audit, transparency, settings)
  const [activeNav, setActiveNav] = useState(getInitialView);
  const [isBackendModalOpen, setIsBackendModalOpen] = useState(false);
  const [isServicesModalOpen, setIsServicesModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);

  // Sync hash with active view
  useEffect(() => {
    function handleHashChange() {
      const hash = window.location.hash.replace(/^#\/?/, '').trim().toLowerCase();
      if (VALID_VIEWS.has(hash)) {
        setActiveNav(hash);
      }
    }
    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  const handleSelectNav = useCallback((viewKey) => {
    if (VALID_VIEWS.has(viewKey)) {
      setActiveNav(viewKey);
      if (typeof window !== 'undefined') {
        window.location.hash = `#${viewKey}`;
      }
    }
  }, []);

  // Jump from another view (e.g. Services, Topology, Findings) directly into Impact Studio with component selected
  const handleSelectServiceAndStudio = useCallback((serviceId) => {
    if (serviceId) {
      setProposedServiceId(serviceId);
    }
    handleSelectNav('studio');
  }, [handleSelectNav]);

  // Open demo project immediately
  const handleOpenDemoProject = useCallback(async () => {
    setApiError(null);
    try {
      let demo = projects.find((p) => p.name && p.name.includes('[Demo]'));
      if (!demo) {
        demo = await fetchDemoProject();
      }
      if (demo && demo.id) {
        setSelectedProjectId(demo.id);
        if (typeof window !== 'undefined' && window.sessionStorage) {
          window.sessionStorage.setItem('impactgraph_selected_project_id', String(demo.id));
        }
        setProposedServiceId('Payment Service');
        setAnalyzedServiceId(null);
        setImpactResult(null);
        handleSelectNav('overview');
      }
    } catch (err) {
      console.warn('Failed to open demo project:', err);
    }
  }, [projects, handleSelectNav]);

  const handleOpenImportModal = useCallback(() => {
    setIsImportModalOpen(true);
  }, []);

  const handleProjectImported = useCallback(async (analysisResult, navigateToStudio = false) => {
    try {
      const refreshedProjects = await fetchProjects({ timeout: 4000 });
      setProjects(refreshedProjects);
      if (analysisResult?.projectId) {
        setSelectedProjectId(analysisResult.projectId);
        if (typeof window !== 'undefined' && window.sessionStorage) {
          window.sessionStorage.setItem('impactgraph_selected_project_id', String(analysisResult.projectId));
        }
        if (analysisResult.services && analysisResult.services.length > 0) {
          setProposedServiceId(analysisResult.services[0].name);
        }
        setAnalyzedServiceId(null);
        setImpactResult(null);
        if (navigateToStudio) {
          handleSelectNav('studio');
        }
      }
    } catch (err) {
      console.warn('Failed to refresh projects after import:', err);
    }
  }, [handleSelectNav]);

  // Logout handler
  const handleLogout = useCallback(() => {
    logout();
    if (typeof window !== 'undefined' && window.sessionStorage) {
      window.sessionStorage.removeItem('impactgraph_selected_project_id');
    }
    setAuthToken(null);
    setCurrentUser(null);
    setProjects([]);
    setSelectedProjectId(null);
    setGraphData(null);
    setImpactResult(null);
    setAnalyzedServiceId(null);
    setApiError(null);
    setFindings([]);
    setHistoryRuns([]);
    setAuditEvents([]);
  }, []);

  // Primary Health Check function
  const checkHealth = useCallback(async () => {
    setBackendLoading(true);
    setBackendError(null);

    try {
      const data = await checkBackendHealth({ timeout: 4000 });
      setHealthData(data);
      setBackendError(null);

      // Refresh persisted projects list when authenticated and backend is accessible
      if (getStoredToken()) {
        try {
          const projectsList = await fetchProjects({ timeout: 4000 });
          setProjects(projectsList);
          if (projectsList && projectsList.length > 0) {
            setSelectedProjectId((prev) => {
              if (prev != null && projectsList.some((p) => p.id === prev)) {
                return prev;
              }
              const demo = projectsList.find((p) => p.name && p.name.includes('[Demo]'));
              if (demo) {
                if (typeof window !== 'undefined' && window.sessionStorage) {
                  window.sessionStorage.setItem('impactgraph_selected_project_id', String(demo.id));
                }
                return demo.id;
              }
              return prev;
            });
          }
        } catch (err) {
          if (err?.status === 401) {
            handleLogout();
          }
        }
      }
    } catch (err) {
      setBackendError(
        err instanceof Error ? err.message : 'Failed to connect to backend server'
      );
      setHealthData(null);
    } finally {
      setBackendLoading(false);
      setLastChecked(new Date().toLocaleTimeString());
    }
  }, [handleLogout]);

  // Login success handler
  const handleLoginSuccess = useCallback(async (authResponse, options = {}) => {
    setAuthToken(authResponse.token);
    setCurrentUser({
      userId: authResponse.userId,
      email: authResponse.email,
      displayName: authResponse.displayName,
      role: authResponse.role,
    });
    setApiError(null);
    await checkHealth();

    if (options?.openDemo) {
      try {
        const demo = await fetchDemoProject();
        if (demo && demo.id) {
          setSelectedProjectId(demo.id);
          if (typeof window !== 'undefined' && window.sessionStorage) {
            window.sessionStorage.setItem('impactgraph_selected_project_id', String(demo.id));
          }
          setProposedServiceId('Payment Service');
          handleSelectNav('overview');
        }
      } catch (err) {
        console.warn('Could not auto-select demo project:', err);
      }
    }
  }, [checkHealth, handleSelectNav]);

  // Initial backend check and projects loading on mount
  useEffect(() => {
    let ignore = false;
    async function initHealth() {
      try {
        const data = await checkBackendHealth({ timeout: 4000 });
        if (!ignore) {
          setHealthData(data);
          setBackendError(null);
        }

        if (getStoredToken()) {
          try {
            const projectsList = await fetchProjects({ timeout: 4000 });
            if (!ignore) {
              setProjects(projectsList);
              if (projectsList && projectsList.length > 0) {
                setSelectedProjectId((prev) => {
                  if (prev != null && projectsList.some((p) => p.id === prev)) {
                    return prev;
                  }
                  const demo = projectsList.find((p) => p.name && p.name.includes('[Demo]'));
                  if (demo) {
                    if (typeof window !== 'undefined' && window.sessionStorage) {
                      window.sessionStorage.setItem('impactgraph_selected_project_id', String(demo.id));
                    }
                    return demo.id;
                  }
                  return prev;
                });
              }
            }
          } catch (err) {
            if (!ignore && err?.status === 401) {
              handleLogout();
            }
          }
        }
      } catch (err) {
        if (!ignore) {
          setBackendError(
            err instanceof Error ? err.message : 'Failed to connect to backend server'
          );
          setHealthData(null);
        }
      } finally {
        if (!ignore) {
          setBackendLoading(false);
          setLastChecked(new Date().toLocaleTimeString());
        }
      }
    }

    initHealth();
    return () => {
      ignore = true;
    };
  }, [handleLogout]);

  // Load telemetry (findings, runs, audit) for active project
  useEffect(() => {
    let ignore = false;
    async function loadTelemetry() {
      if (!authToken) return;
      try {
        const [fList, hList, aList] = await Promise.all([
          fetchSecurityFindings({ projectId: selectedProjectId }).catch(() => []),
          fetchAnalysisHistory(selectedProjectId).catch(() => []),
          fetchAuditEvents({ size: 10, projectId: selectedProjectId })
            .then((r) => r?.content || [])
            .catch(() => []),
        ]);
        if (!ignore) {
          setFindings(fList || []);
          setHistoryRuns(hList || []);
          setAuditEvents(aList || []);
        }
      } catch (err) {
        console.warn('Telemetry loading failed:', err);
      }
    }
    loadTelemetry();
    return () => {
      ignore = true;
    };
  }, [selectedProjectId, authToken]);

  // Load graph topology whenever the preset or selected project changes
  useEffect(() => {
    const controller = new AbortController();

    async function loadGraph() {
      try {
        if (selectedProjectId != null) {
          const data = await fetchProjectGraph(selectedProjectId, {
            signal: controller.signal,
            timeout: 5000,
          });
          setGraphData(data);
          setApiError(null);

          if (data.services && data.services.length > 0) {
            setProposedServiceId((prev) => {
              const exists = data.services.some((s) => s.id === prev);
              return exists ? prev : data.services[0].id;
            });
          } else {
            setProposedServiceId('');
          }
        } else {
          const data = await fetchServiceGraph(currentPreset, {
            signal: controller.signal,
            timeout: 5000,
          });
          setGraphData(data);
          setApiError(null);

          if (data.services && data.services.length > 0) {
            setProposedServiceId((prev) => {
              const exists = data.services.some((s) => s.id === prev);
              return exists ? prev : data.services[0].id;
            });
          }
        }
      } catch (err) {
        if (err.name !== 'AbortError') {
          if (err?.status === 401) {
            handleLogout();
            return;
          }
          setApiError(err.message || 'Failed to load graph topology from backend');
          if (selectedProjectId != null) {
            setGraphData(null);
            setProposedServiceId('');
          }
        }
      }
    }

    if (authToken) {
      loadGraph();
    }

    return () => {
      controller.abort();
    };
  }, [currentPreset, selectedProjectId, authToken, handleLogout]);

  // Execute explicit change impact analysis
  const handleAnalyze = useCallback(async () => {
    if (!proposedServiceId) {
      return;
    }

    setImpactLoading(true);
    setApiError(null);

    try {
      const result = await analyzeChangeImpact(proposedServiceId, currentPreset, {
        projectId: selectedProjectId,
        timeout: 5000,
      });
      setImpactResult(result);
      setAnalyzedServiceId(proposedServiceId);
      setApiError(null);
    } catch (err) {
      if (err?.status === 401) {
        handleLogout();
        return;
      }
      setApiError(err.message || 'Impact analysis request failed');
      setImpactResult(null);
      setAnalyzedServiceId(null);
    } finally {
      setImpactLoading(false);
    }
  }, [proposedServiceId, currentPreset, selectedProjectId, handleLogout]);

  // Handle service selection
  const handleSelectService = useCallback(
    (serviceId) => {
      setProposedServiceId(serviceId);
      if (serviceId !== analyzedServiceId) {
        setAnalyzedServiceId(null);
        setImpactResult(null);
        setApiError(null);
      }
    },
    [analyzedServiceId]
  );

  const handlePresetChange = (preset) => {
    setSelectedProjectId(null);
    if (typeof window !== 'undefined' && window.sessionStorage) {
      window.sessionStorage.removeItem('impactgraph_selected_project_id');
    }
    setCurrentPreset(preset);
    setAnalyzedServiceId(null);
    setImpactResult(null);
    setApiError(null);
  };

  const handleSelectProject = (projectId) => {
    setSelectedProjectId(projectId);
    if (typeof window !== 'undefined' && window.sessionStorage) {
      if (projectId != null) {
        window.sessionStorage.setItem('impactgraph_selected_project_id', String(projectId));
      } else {
        window.sessionStorage.removeItem('impactgraph_selected_project_id');
      }
    }
    setAnalyzedServiceId(null);
    setImpactResult(null);
    setApiError(null);
  };

  // Render Login Screen if user has no active authentication token or profile
  if (!authToken || !currentUser) {
    return (
      <LoginView
        onLoginSuccess={handleLoginSuccess}
        healthData={healthData}
      />
    );
  }

  const services = graphData?.services || [];
  const dependencies = graphData?.dependencies || [];
  const isDemoFallback = Boolean(graphData?.isFallback || impactResult?.isFallback);
  const activeProject = projects.find((p) => p.id === selectedProjectId);
  const isDemoActive = Boolean(activeProject?.name?.includes('[Demo]') || (!selectedProjectId && currentPreset));

  return (
    <div className="app-shell">
      {/* 1. Top Navigation Bar */}
      <TopNav
        services={services}
        selectedServiceId={proposedServiceId}
        onSelectService={(id) => {
          handleSelectService(id);
          handleSelectNav('studio');
        }}
        healthData={healthData}
        backendLoading={backendLoading}
        onRefresh={checkHealth}
        onOpenBackendDetails={() => setIsBackendModalOpen(true)}
        isDemoFallback={isDemoFallback}
        user={currentUser}
        onLogout={handleLogout}
        onOpenDemoProject={handleOpenDemoProject}
        isDemoActive={isDemoActive}
        onOpenImportModal={handleOpenImportModal}
      />

      <div className="app-body-container">
        {/* 2. Left Multi-View Sidebar */}
        <Sidebar
          activeNav={activeNav}
          onSelectNav={handleSelectNav}
          user={currentUser}
        />

        {/* 3. Dynamic Multi-View Workspace */}
        <main className="main-workspace" id="main-content" tabIndex={-1}>
          {/* Honest Demo Fallback Notification Banner */}
          {isDemoFallback && !selectedProjectId && (
            <div className="demo-fallback-banner" role="status">
              <div className="demo-banner-left">
                <span className="demo-banner-badge">Offline Mode</span>
                <span>
                  Backend server is offline. Local AST simulation fixtures are active.
                </span>
              </div>
              <button
                type="button"
                className="demo-banner-retry-btn"
                onClick={checkHealth}
                title="Retry connecting to live Spring Boot backend"
              >
                Retry Connection
              </button>
            </div>
          )}

          {/* Explicit Server Error Banner */}
          {apiError && (
            <div className="api-error-banner" role="alert">
              <div className="api-error-left">
                <span className="api-error-badge">API Error</span>
                <span>{apiError}</span>
              </div>
              <button
                type="button"
                className="api-error-close-btn"
                onClick={() => setApiError(null)}
                aria-label="Dismiss error"
              >
                &times;
              </button>
            </div>
          )}

          {/* View Routing Switch */}
          {activeNav === 'overview' && (
            <OverviewView
              services={services}
              dependencies={dependencies}
              graphData={graphData}
              user={currentUser}
              onNavigate={handleSelectNav}
              onSelectServiceAndStudio={handleSelectServiceAndStudio}
              selectedProjectId={selectedProjectId}
              currentPreset={currentPreset}
              findings={findings}
              historyRuns={historyRuns}
              auditEvents={auditEvents}
              onOpenDemo={handleOpenDemoProject}
              isDemoProject={isDemoActive}
              onOpenImportModal={handleOpenImportModal}
            />
          )}

          {activeNav === 'studio' && (
            <ImpactStudioView
              services={services}
              dependencies={dependencies}
              graphData={graphData}
              proposedServiceId={proposedServiceId}
              analyzedServiceId={analyzedServiceId}
              impactResult={impactResult}
              impactLoading={impactLoading}
              onSelectService={handleSelectService}
              onAnalyze={handleAnalyze}
              currentPreset={currentPreset}
              onChangePreset={handlePresetChange}
              projects={projects}
              selectedProjectId={selectedProjectId}
              onSelectProject={handleSelectProject}
              findings={findings}
              onOpenImportModal={handleOpenImportModal}
            />
          )}

          {activeNav === 'services' && (
            <ServicesView
              services={services}
              dependencies={dependencies}
              graphData={graphData}
              onSelectServiceAndStudio={handleSelectServiceAndStudio}
              selectedProjectId={selectedProjectId}
              currentPreset={currentPreset}
            />
          )}

          {activeNav === 'topology' && (
            <TopologyView
              services={services}
              dependencies={dependencies}
              graphData={graphData}
              onSelectServiceAndStudio={handleSelectServiceAndStudio}
              selectedProjectId={selectedProjectId}
              currentPreset={currentPreset}
            />
          )}

          {activeNav === 'apis' && (
            <ApiSecurityView
              onSelectServiceAndStudio={handleSelectServiceAndStudio}
              selectedProjectId={selectedProjectId}
            />
          )}

          {activeNav === 'findings' && (
            <SecurityFindingsView
              onSelectServiceAndStudio={handleSelectServiceAndStudio}
              selectedProjectId={selectedProjectId}
            />
          )}

          {activeNav === 'history' && (
            <AnalysisHistoryView
              onNavigate={handleSelectNav}
              onSelectServiceAndStudio={handleSelectServiceAndStudio}
              selectedProjectId={selectedProjectId}
            />
          )}

          {activeNav === 'audit' && (
            <AuditExplorerView
              user={currentUser}
            />
          )}

          {activeNav === 'transparency' && (
            <TransparencyView />
          )}

          {activeNav === 'settings' && (
            <SettingsView
              user={currentUser}
              onLogout={handleLogout}
            />
          )}
        </main>
      </div>

      {/* Backend Health Status & Details Modal */}
      <BackendStatusModal
        isOpen={isBackendModalOpen}
        onClose={() => setIsBackendModalOpen(false)}
        healthData={healthData}
        loading={backendLoading}
        error={backendError}
        onRefresh={checkHealth}
        lastChecked={lastChecked}
        apiBaseUrl={API_BASE_URL}
      />

      {/* Services Directory Modal for search quick access */}
      <ServicesDirectoryModal
        isOpen={isServicesModalOpen}
        onClose={() => setIsServicesModalOpen(false)}
        services={services}
        selectedServiceId={proposedServiceId}
        onSelectService={(id) => {
          handleSelectService(id);
          handleSelectNav('studio');
        }}
      />

      {/* Real Repository Architecture Import Modal */}
      <ImportRepositoryModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        onProjectImported={handleProjectImported}
      />
    </div>
  );
}

export default App;
