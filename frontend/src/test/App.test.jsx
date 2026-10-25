import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import App from '../App';

describe('ImpactGraph Frontend Portal', () => {
  beforeEach(() => {
    localStorage.clear();
    // Default fetch mock for /api/health and /api/transparency/public
    global.fetch = vi.fn((url) => {
      if (url === '/api/health') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ status: 'UP', service: 'impactgraph-backend' }),
          text: () => Promise.resolve('{"status":"UP"}'),
        });
      }
      if (url === '/api/transparency/public') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({
            dataCollected: [
              {
                category: 'Account Identity',
                fields: ['Email address', 'Display name'],
                purpose: 'Authentication',
              },
            ],
            dataStorage: { database: 'PostgreSQL' },
            graphAnalysis: { status: 'Under development' },
            accessControl: { ADMIN: 'Full access', DEVELOPER: 'Restricted access' },
            dataRetention: { auditEvents: 'Indefinite' },
          }),
        });
      }
      return Promise.reject(new Error(`Unhandled request: ${url}`));
    });
  });

  it('renders brand header, hero section, and core security metrics', async () => {
    await act(async () => {
      render(<App />);
    });

    // Brand and Hub
    expect(screen.getByText('Security Hub')).toBeInTheDocument();
    expect(screen.getByText(/Enterprise Security &/i)).toBeInTheDocument();
    expect(screen.getByText(/Transparency Center/i)).toBeInTheDocument();

    // Security Metrics
    expect(screen.getByText('HS512')).toBeInTheDocument();
    expect(screen.getByText('BCrypt-12')).toBeInTheDocument();
    expect(screen.getByText('Audit Immutability')).toBeInTheDocument();
    expect(screen.getByText('RBAC + IDOR')).toBeInTheDocument();
  });

  it('renders the Public Data Transparency tab and RBAC matrix table', async () => {
    await act(async () => {
      render(<App />);
    });

    // Tab content
    expect(screen.getByText(/Public Data Transparency Portal/i)).toBeInTheDocument();
    expect(screen.getByText(/1. Categories of Data Collected/i)).toBeInTheDocument();
    expect(screen.getByText(/3. Role-Based Access Control \(RBAC\) Matrix/i)).toBeInTheDocument();

    // Table rows
    expect(screen.getByText('ADMIN')).toBeInTheDocument();
    expect(screen.getByText('DEVELOPER')).toBeInTheDocument();
    expect(screen.getByText('PUBLIC / ANONYMOUS')).toBeInTheDocument();
  });

  it('navigates to My Activity tab and prompts unauthenticated users to sign in', async () => {
    await act(async () => {
      render(<App />);
    });

    const myActivityTab = screen.getByRole('button', { name: /My Activity/i });
    await act(async () => {
      fireEvent.click(myActivityTab);
    });

    expect(screen.getByText(/User Activity Transparency/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Authentication Required/i })).toBeInTheDocument();
    expect(screen.getByText(/Sign in to inspect your user account telemetry/i)).toBeInTheDocument();
  });

  it('navigates to Security Sandbox tab and displays API assertion cards', async () => {
    await act(async () => {
      render(<App />);
    });

    const sandboxTab = screen.getByRole('button', { name: /Security Sandbox/i });
    await act(async () => {
      fireEvent.click(sandboxTab);
    });

    expect(screen.getByText(/Security Verification Sandbox/i)).toBeInTheDocument();
    expect(screen.getByText('GET /api/health')).toBeInTheDocument();
    expect(screen.getByText('GET /api/projects')).toBeInTheDocument();
    expect(screen.getByText('POST /api/audit/events')).toBeInTheDocument();
    expect(screen.getByText('GET /api/audit/events')).toBeInTheDocument();
  });

  it('opens authentication modal and toggles between Sign In and Register', async () => {
    await act(async () => {
      render(<App />);
    });

    const openAuthBtn = screen.getByRole('button', { name: /Sign In \/ Register/i });
    await act(async () => {
      fireEvent.click(openAuthBtn);
    });

    // Modal opens in login mode by default
    expect(screen.getByRole('heading', { name: /Sign In to ImpactGraph/i })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Alice Engineer')).not.toBeInTheDocument();

    // Switch to register mode
    const registerLink = screen.getByText('Register here');
    await act(async () => {
      fireEvent.click(registerLink);
    });

    expect(screen.getByRole('heading', { name: /Create an Account/i })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Alice Engineer')).toBeInTheDocument();
    expect(screen.getByText(/The first user registered in ImpactGraph automatically receives the/i)).toBeInTheDocument();

    // Close modal
    const closeBtn = screen.getByText('✕');
    await act(async () => {
      fireEvent.click(closeBtn);
    });

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /Create an Account/i })).not.toBeInTheDocument();
    });
  });

  it('executes a sandbox test and renders response details', async () => {
    await act(async () => {
      render(<App />);
    });

    const sandboxTab = screen.getByRole('button', { name: /Security Sandbox/i });
    await act(async () => {
      fireEvent.click(sandboxTab);
    });

    // Click Run Assertion on the Health Check card
    const runAssertionButtons = screen.getAllByRole('button', { name: /Run Assertion →/i });
    await act(async () => {
      fireEvent.click(runAssertionButtons[0]);
    });

    await waitFor(() => {
      expect(screen.getByText(/Test:/i)).toBeInTheDocument();
      expect(screen.getByText(/Status: 200/i)).toBeInTheDocument();
    });
  });
});
