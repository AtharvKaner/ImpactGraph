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
} from './services/impactService';
import { TopNav } from './components/TopNav';
import { Sidebar } from './components/Sidebar';
import { CycleIndicator } from './components/CycleIndicator';
import { ServiceSelector } from './components/ServiceSelector';
import { GraphView } from './components/GraphView';
import { ImpactPanel } from './components/ImpactPanel';
import { BackendStatusModal } from './components/BackendStatusModal';
import { ServicesDirectoryModal } from './components/ServicesDirectoryModal';
import { LoginView } from './components/LoginView';
import './App.css';

function App() {
  // Authentication session state restored from sessionStorage on page load / refresh
  const [currentUser, setCurrentUser] = useState(() => getStoredUser());
  const [authToken, setAuthToken] = useState(() => getStoredToken());

  // Backend health status state
  const [healthData, setHealthData] = useState(null);
  const [backendLoading, setBackendLoading] = useState(true);
  const [backendError, setBackendError] = useState(null);
  const [lastChecked, setLastChecked] = useState(null);

  // Persisted projects state
  const [projects, setProjects] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState(null);

  // Impact analysis dashboard state
  const [currentPreset, setCurrentPreset] = useState(DATASET_PRESETS.ECOMMERCE_DAG);
  const [graphData, setGraphData] = useState(null);

  // Proposed change target vs executed analysis target
  const [proposedServiceId, setProposedServiceId] = useState('Order Service');
  const [analyzedServiceId, setAnalyzedServiceId] = useState(null);
  const [impactResult, setImpactResult] = useState(null);
  const [impactLoading, setImpactLoading] = useState(false);

  // Server error banner state (e.g. HTTP 400 or 404 responses)
  const [apiError, setApiError] = useState(null);

  // UI state for navigation and modals
  const [activeNav, setActiveNav] = useState('dashboard');
  const [isBackendModalOpen, setIsBackendModalOpen] = useState(false);
  const [isServicesModalOpen, setIsServicesModalOpen] = useState(false);

  // Logout handler: clears session storage, resets user/auth state, and clears loaded project data
  const handleLogout = useCallback(() => {
    logout();
    setAuthToken(null);
    setCurrentUser(null);
    setProjects([]);
    setGraphData(null);
    setImpactResult(null);
    setAnalyzedServiceId(null);
    setApiError(null);
  }, []);

  // Primary Health Check function (e.g. for user refresh button)
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

  // Login success handler: records session token and user profile into state
  const handleLoginSuccess = useCallback((authResponse) => {
    setAuthToken(authResponse.token);
    setCurrentUser({
      userId: authResponse.userId,
      email: authResponse.email,
      displayName: authResponse.displayName,
      role: authResponse.role,
    });
    setApiError(null);
    checkHealth();
  }, [checkHealth]);

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

          // Update proposed service selection for the loaded project
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

  // If already analyzed and user toggles architecture preset, re-evaluate automatically
  useEffect(() => {
    if (!analyzedServiceId || !authToken) {
      return;
    }

    const controller = new AbortController();
    let ignore = false;

    async function reAnalyze() {
      setImpactLoading(true);
      try {
        const result = await analyzeChangeImpact(analyzedServiceId, currentPreset, {
          projectId: selectedProjectId,
          signal: controller.signal,
          timeout: 5000,
        });
        if (!ignore) {
          setImpactResult(result);
          setApiError(null);
        }
      } catch (err) {
        if (!ignore && err.name !== 'AbortError') {
          if (err?.status === 401) {
            handleLogout();
            return;
          }
          setApiError(err.message || 'Impact analysis request failed');
        }
      } finally {
        if (!ignore) {
          setImpactLoading(false);
        }
      }
    }

    reAnalyze();

    return () => {
      ignore = true;
      controller.abort();
    };
  }, [currentPreset, selectedProjectId, analyzedServiceId, authToken, handleLogout]);

  // Handle service selection (from dropdown, graph node click, or suggested scenario chips)
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
    setCurrentPreset(preset);
    setAnalyzedServiceId(null);
    setImpactResult(null);
    setApiError(null);
  };

  const handleSelectProject = (projectId) => {
    setSelectedProjectId(projectId);
    setAnalyzedServiceId(null);
    setImpactResult(null);
    setApiError(null);
  };

  const handleNavClick = (navKey) => {
    setActiveNav(navKey);
    if (navKey === 'dashboard') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } else if (navKey === 'impact') {
      const el = document.querySelector('.impact-panel-card');
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } else if (navKey === 'topology') {
      const el = document.querySelector('.topology-health-card');
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
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

  return (
    <div className="app-shell">
      {/* 1. Compact Top Navigation Bar */}
      <TopNav
        services={services}
        selectedServiceId={proposedServiceId}
        onSelectService={handleSelectService}
        healthData={healthData}
        backendLoading={backendLoading}
        onRefresh={checkHealth}
        onOpenBackendDetails={() => setIsBackendModalOpen(true)}
        isDemoFallback={isDemoFallback}
        user={currentUser}
        onLogout={handleLogout}
      />

      <div className="app-body-container">
        {/* 2. Slim Left Sidebar */}
        <Sidebar
          activeNav={activeNav}
          onSelectNav={handleNavClick}
          onOpenSettings={() => setIsBackendModalOpen(true)}
          onOpenServicesModal={() => setIsServicesModalOpen(true)}
        />

        {/* 3. Main Dashboard Workspace */}
        <main className="main-workspace">
          {/* Main heading */}
          <div className="main-header-block">
            <h1 className="main-title">Software Change Impact Analysis</h1>
            <p className="main-desc">
              Simulate service modifications against demonstration architectures or persisted PostgreSQL projects to calculate blast radius and detect circular dependencies.
            </p>
          </div>

          {/* Honest Demo Fallback Notification Banner */}
          {isDemoFallback && !selectedProjectId && (
            <div className="demo-fallback-banner" role="status">
              <div className="demo-banner-left">
                <span className="demo-banner-badge">Offline Demo</span>
                <span>
                  Backend server is offline or unreachable. Displaying local demonstration dataset.
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

          {/* Explicit Server Error Banner (e.g. 400 Bad Request, 404 Not Found) */}
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

          {/* 4. Compact Topology Health Summary */}
          <CycleIndicator
            cycleResult={
              impactResult?.cycleResult || {
                hasCycle: graphData?.hasCycle,
                cycles: graphData?.cycles || graphData?.cycleDetails || [],
              }
            }
            presetName={graphData?.name || 'Loading topology...'}
          />

          {/* 5. Control Bar: Architecture Setup + Simulated Software Change */}
          <ServiceSelector
            services={services}
            proposedServiceId={proposedServiceId}
            onSelectService={handleSelectService}
            onAnalyze={handleAnalyze}
            currentPreset={currentPreset}
            onChangePreset={handlePresetChange}
            projects={projects}
            selectedProjectId={selectedProjectId}
            onSelectProject={handleSelectProject}
            loading={impactLoading}
            hasAnalyzed={Boolean(
              impactResult && analyzedServiceId && analyzedServiceId === proposedServiceId
            )}
            edgeCount={dependencies.length}
            hasCycle={graphData?.hasCycle}
          />

          {/* 6 & 7. Large Service Dependency Graph + Clean Impact Analysis Panel */}
          <div className="dashboard-grid">
            <GraphView
              services={services}
              dependencies={dependencies}
              proposedServiceId={proposedServiceId}
              analyzedServiceId={analyzedServiceId}
              impactResult={impactResult}
              onNodeClick={handleSelectService}
            />

            <ImpactPanel
              proposedServiceId={proposedServiceId}
              analyzedServiceId={analyzedServiceId}
              services={services}
              impactResult={impactResult}
              loading={impactLoading}
              onAnalyze={handleAnalyze}
              onSelectService={handleSelectService}
              currentPresetName={graphData?.name}
            />
          </div>
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

      {/* Services Directory Modal */}
      <ServicesDirectoryModal
        isOpen={isServicesModalOpen}
        onClose={() => setIsServicesModalOpen(false)}
        services={services}
        selectedServiceId={proposedServiceId}
        onSelectService={handleSelectService}
      />
    </div>
  );
}

export default App;
