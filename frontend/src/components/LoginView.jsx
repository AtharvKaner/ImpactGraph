import React, { useState } from 'react';
import {
  IconLogo,
  IconLock,
  IconAlertTriangle,
  IconEye,
  IconEyeOff,
  IconCheckShield,
} from './Icons';
import { login } from '../services/impactService';
import './LoginView.css';

export function LoginView({ onLoginSuccess, healthData = null }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password) {
      setErrorMessage('Please provide both your email address and password.');
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    try {
      const authResponse = await login(email.trim(), password);
      if (onLoginSuccess) {
        onLoginSuccess(authResponse);
      }
    } catch (err) {
      if (err?.status === 401) {
        setErrorMessage('Invalid email or password. Please verify your credentials and try again.');
      } else if (err?.status === 403) {
        setErrorMessage('Access forbidden. Your account does not have permission to access this portal.');
      } else if (err?.message && !err?.message.includes('object') && err.message.length < 120) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage('Unable to connect to the authentication server. Please check your backend connection.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleOpenDemo = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      // First attempt authenticating with seeded demo account
      const authResponse = await login('demo@impactgraph.io', 'DemoPassword123!');
      if (onLoginSuccess) {
        onLoginSuccess(authResponse, { openDemo: true });
      }
    } catch {
      // Fallback: If login fails (e.g. backend offline), log in as guest demo user
      if (onLoginSuccess) {
        onLoginSuccess({
          token: 'demo-guest-jwt-token',
          userId: 'demo-user-id',
          email: 'demo@impactgraph.io',
          displayName: 'Demo Engineer',
          role: 'DEVELOPER',
        }, { openDemo: true });
      }
    } finally {
      setLoading(false);
    }
  };

  const isBackendOnline = Boolean(healthData && healthData.status === 'UP');

  return (
    <div className="login-page-wrapper">
      <div className="login-container">
        {/* Brand Header */}
        <div className="login-brand-header">
          <div className="login-brand-logo-wrap">
            <IconLogo size={42} />
          </div>
          <h1 className="login-brand-title">ImpactGraph</h1>
          <p className="login-brand-tagline">
            Microservices Dependency Analysis & Change Impact Simulation
          </p>
        </div>

        {/* Login Card */}
        <div className="login-card">
          <div className="login-card-head">
            <div className="login-lock-badge">
              <IconLock size={13} />
              <span>Enterprise Authentication</span>
            </div>
            <h2 className="login-heading">Sign In to Your Workspace</h2>
            <p className="login-subheading">
              Authenticate with your credentials to access live project topologies and run blast radius simulations.
            </p>
          </div>

          {/* User-facing error message banner */}
          {errorMessage && (
            <div className="login-error-banner" role="alert">
              <IconAlertTriangle size={17} className="login-error-icon" />
              <span className="login-error-msg">{errorMessage}</span>
              <button
                type="button"
                className="login-error-dismiss"
                onClick={() => setErrorMessage(null)}
                aria-label="Dismiss error"
              >
                &times;
              </button>
            </div>
          )}

          {/* Login Form */}
          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <div className="login-field-group">
              <label htmlFor="login-email-input" className="login-field-label">
                Email Address
              </label>
              <div className="login-input-wrap">
                <input
                  id="login-email-input"
                  type="email"
                  className="login-input"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@company.com"
                  autoComplete="email"
                  autoFocus
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="login-field-group">
              <label htmlFor="login-password-input" className="login-field-label">
                Password
              </label>
              <div className="login-input-wrap">
                <input
                  id="login-password-input"
                  type={showPassword ? 'text' : 'password'}
                  className="login-input has-toggle"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  title={showPassword ? 'Hide password' : 'Show password'}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? <IconEyeOff size={16} /> : <IconEye size={16} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="login-submit-btn"
              disabled={loading || !email.trim() || !password}
            >
              {loading ? (
                <>
                  <span className="btn-spinner" />
                  <span>Signing In...</span>
                </>
              ) : (
                <span>Sign In →</span>
              )}
            </button>

            <div className="login-divider">
              <span>OR</span>
            </div>

            <button
              type="button"
              className="login-demo-action-btn"
              onClick={handleOpenDemo}
              disabled={loading}
              title="Open ready-to-explore multi-service architecture demo"
            >
              <div className="login-demo-btn-left">
                <span className="demo-lightning-icon">⚡</span>
                <div className="demo-btn-texts">
                  <span className="demo-btn-heading">Open Demo Project (Instant Preview)</span>
                  <span className="demo-btn-desc">Explore 8-service e-commerce topology, blast radius & security findings</span>
                </div>
              </div>
              <span className="demo-btn-arrow">&rarr;</span>
            </button>

            <p className="login-demo-credentials">
              Local demo sign-in: <strong>demo@impactgraph.io</strong> / <strong>DemoPassword123!</strong>
              <br />Start the backend with the <code>dev</code> profile to create this local-only account.
            </p>
          </form>

          {/* Footer Security Badges */}
          <div className="login-card-footer">
            <div className="login-footer-security">
              <IconCheckShield size={14} className="text-primary" />
              <span>Secure Role-Based Authentication &amp; Access Control</span>
            </div>
            <div className="login-backend-status-line">
              <span>Analysis Engine:</span>
              <span className={`backend-indicator ${isBackendOnline ? 'online' : 'offline'}`}>
                ● {isBackendOnline ? 'Service Online' : 'Connecting / Standby'}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
