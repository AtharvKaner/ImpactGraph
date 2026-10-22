import { useState, useEffect } from 'react'
import './App.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

function App() {
  const [healthData, setHealthData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastChecked, setLastChecked] = useState(null)

  const checkHealth = async () => {
    setLoading(true)
    setError(null)
    const endpoint = `${API_BASE_URL}/api/health`

    try {
      const response = await fetch(endpoint, {
        method: 'GET',
        headers: {
          'Accept': 'application/json'
        }
      })

      if (!response.ok) {
        throw new Error(`Backend responded with HTTP status ${response.status} (${response.statusText})`)
      }

      const data = await response.json()
      setHealthData(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to connect to backend server')
      setHealthData(null)
    } finally {
      setLoading(false)
      setLastChecked(new Date().toLocaleTimeString())
    }
  }

  useEffect(() => {
    let ignore = false
    const fetchInitialStatus = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/health`, {
          headers: { 'Accept': 'application/json' }
        })
        if (!response.ok) {
          throw new Error(`Backend responded with HTTP status ${response.status} (${response.statusText})`)
        }
        const data = await response.json()
        if (!ignore) {
          setHealthData(data)
          setLoading(false)
          setLastChecked(new Date().toLocaleTimeString())
        }
      } catch (err) {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Failed to connect to backend server')
          setHealthData(null)
          setLoading(false)
          setLastChecked(new Date().toLocaleTimeString())
        }
      }
    }

    fetchInitialStatus()
    return () => {
      ignore = true
    }
  }, [])

  return (
    <div className="impactgraph-container">
      <header className="header">
        <div className="logo-badge">IG</div>
        <div className="header-text">
          <h1 className="title">ImpactGraph</h1>
          <p className="subtitle">Software Change Impact Analysis</p>
        </div>
      </header>

      <main className="content">
        <section className="status-card">
          <div className="card-header">
            <h2>Backend System Status</h2>
            <button
              className="refresh-btn"
              onClick={checkHealth}
              disabled={loading}
            >
              {loading ? 'Checking...' : 'Refresh Status'}
            </button>
          </div>

          <div className="status-indicator-section">
            <div className="status-indicator">
              <span
                className={`status-dot ${
                  loading ? 'loading' : healthData ? 'up' : 'down'
                }`}
              />
              <span className="status-text">
                {loading
                  ? 'Connecting to backend...'
                  : healthData
                  ? 'Backend Reachable (UP)'
                  : 'Backend Unreachable (DOWN)'}
              </span>
            </div>
            {lastChecked && (
              <span className="timestamp-note">Last evaluated: {lastChecked}</span>
            )}
          </div>

          <div className="status-details">
            <div className="detail-row">
              <span className="detail-label">Endpoint:</span>
              <code className="detail-value">{API_BASE_URL}/api/health</code>
            </div>

            {healthData && (
              <>
                <div className="detail-row">
                  <span className="detail-label">Service:</span>
                  <span className="detail-value">{healthData.service}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">Reported Status:</span>
                  <span className="detail-value badge-success">{healthData.status}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-label">Server Timestamp:</span>
                  <span className="detail-value">{healthData.timestamp}</span>
                </div>
              </>
            )}

            {error && (
              <div className="error-box">
                <p className="error-title">Connection Error</p>
                <p className="error-message">{error}</p>
                <p className="error-help">
                  Ensure the Spring Boot backend is running on <code>{API_BASE_URL}</code>.
                  <br />
                  Run command in <code>./backend</code>: <code>.\mvnw.cmd spring-boot:run</code>
                </p>
              </div>
            )}
          </div>
        </section>

        <section className="info-card">
          <h3>Project Skeleton - Milestone 1</h3>
          <ul className="info-list">
            <li><strong>Backend:</strong> Java 21, Spring Boot, Maven Wrapper, Spring Data JPA, PostgreSQL</li>
            <li><strong>Security Configuration:</strong> DB secrets externalized to environment variables</li>
            <li><strong>Frontend:</strong> React 19, Vite, React Flow (@xyflow/react)</li>
            <li><strong>Verification:</strong> Health-check verified via JUnit 5 & Mockito test suite</li>
          </ul>
        </section>
      </main>

      <footer className="footer">
        <p>ImpactGraph &bull; Milestone 1: Runnable Project Skeleton</p>
      </footer>
    </div>
  )
}

export default App
