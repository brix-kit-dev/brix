/* @vitest-environment jsdom */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';
import {
  AuthCapabilityType,
  type AuthCapability,
  type AuthRouteDecision,
  type AuthRoutePolicy,
  type AuthState,
  type LoginCredentials,
  type LoginResult,
  type RuntimeContext,
  type User,
  type VerifiedAuthContext,
  type VerifiedSession,
} from '@brix-sdk/runtime-sdk-api-web';
import { PlatformAuthGuard } from './PlatformAuthGuard';
import { TenantAuthGuard } from './TenantAuthGuard';
import { SetupOnlyGuard } from './SetupOnlyGuard';

vi.mock('../hooks/usePlatformBootstrap', () => ({
  usePlatformBootstrap: () => ({
    status: { open: true },
    result: null,
    loading: false,
    error: null,
    refreshStatus: vi.fn(),
    createFirstAdmin: vi.fn(),
    reset: vi.fn(),
  }),
}));

const user: User = {
  id: '1001',
  username: 'operator',
  roles: ['PLATFORM_SUPER_ADMIN'],
  permissions: ['platform:tenant:read'],
};

describe('platform-admin route guards', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders platform children only for a verified platform context', async () => {
    renderGuard(
      <PlatformAuthGuard>
        <span>platform-ok</span>
      </PlatformAuthGuard>,
      platformContext(),
      '/platform',
    );

    expect(await screen.findByText('platform-ok')).toBeTruthy();
  });

  it('rejects actor context from platform routes', async () => {
    renderGuard(
      <Routes>
        <Route
          path="/platform"
          element={(
            <PlatformAuthGuard>
              <span>platform-ok</span>
            </PlatformAuthGuard>
          )}
        />
        <Route path="/platform/login" element={<span>platform-login</span>} />
      </Routes>,
      actorContext(),
      '/platform',
      false,
    );

    expect(await screen.findByText('platform-login')).toBeTruthy();
    expect(screen.queryByText('platform-ok')).toBeNull();
  });

  it('rejects platform context from tenant routes without switching to platform dashboard', async () => {
    renderGuard(
      <Routes>
        <Route
          path="/tenant"
          element={(
            <TenantAuthGuard>
              <span>tenant-ok</span>
            </TenantAuthGuard>
          )}
        />
        <Route path="/login" element={<span>tenant-login</span>} />
        <Route path="/platform" element={<span>platform-dashboard</span>} />
      </Routes>,
      platformContext(),
      '/tenant',
      false,
    );

    expect(await screen.findByText('tenant-login')).toBeTruthy();
    expect(screen.queryByText('platform-dashboard')).toBeNull();
  });

  it('keeps setup tokens in route state and removes them from the URL', async () => {
    renderGuard(
      <Routes>
        <Route
          path="/platform/setup"
          element={(
            <SetupOnlyGuard>
              <SetupStateProbe />
            </SetupOnlyGuard>
          )}
        />
      </Routes>,
      null,
      '/platform/setup?token=setup-token',
      false,
    );

    await waitFor(() => expect(screen.getByTestId('setup-state').textContent).toBe('setup-token'));
    expect(screen.getByTestId('setup-search').textContent).toBe('');
  });
});

function renderGuard(
  element: JSX.Element,
  activeContext: VerifiedAuthContext | null,
  initialPath: string,
  wrapRoutes = true,
): void {
  const runtimeContext: RuntimeContext = {
    moduleId: 'platform-admin',
    tenantId: '',
    getCapability: (capabilityType) => capabilityType === AuthCapabilityType
      ? authCapability(activeContext)
      : undefined,
  };
  const child = wrapRoutes
    ? (
      <Routes>
        <Route path={initialPath.split('?')[0]} element={element} />
        <Route path="/platform/login" element={<span>platform-login</span>} />
        <Route path="/login" element={<span>tenant-login</span>} />
      </Routes>
    )
    : element;

  render(
    <RuntimeContextProvider value={runtimeContext}>
      <MemoryRouter initialEntries={[initialPath]}>
        {child}
      </MemoryRouter>
    </RuntimeContextProvider>,
  );
}

function SetupStateProbe(): JSX.Element {
  const location = useLocation();
  const state = location.state as { setupToken?: string } | null;
  return (
    <>
      <span data-testid="setup-state">{state?.setupToken ?? ''}</span>
      <span data-testid="setup-search">{location.search}</span>
    </>
  );
}

function authCapability(activeContext: VerifiedAuthContext | null): AuthCapability {
  return {
    getCurrentUser: () => activeContext ? user : null,
    isAuthenticated: () => activeContext !== null && activeContext.kind !== 'bootstrap-setup',
    login: async (_credentials: LoginCredentials): Promise<LoginResult> => ({ success: true }),
    logout: async () => undefined,
    hasPermission: (permission: string) => activeContext?.permissions.includes(permission) ?? false,
    hasRole: (role: string) => user.roles.includes(role),
    getToken: () => {
      throw new Error('Guard must not read raw tokens');
    },
    getVerifiedSession: (): VerifiedSession => ({
      state: activeContext
        ? activeContext.kind === 'bootstrap-setup' ? 'challenge' : 'authenticated'
        : 'anonymous',
      activeContext,
      permissions: activeContext?.permissions ?? [],
    }),
    getActiveContext: () => activeContext,
    getVerifiedPlatformContext: () => activeContext?.kind === 'platform' ? activeContext : null,
    getVerifiedActorContext: () => activeContext?.kind === 'actor' ? activeContext : null,
    getVerifiedSubjectContext: () => activeContext?.kind === 'subject' ? activeContext : null,
    getVerifiedBootstrapContext: () => activeContext?.kind === 'bootstrap-setup' ? activeContext : null,
    canAccessRoute: (policy: AuthRoutePolicy): AuthRouteDecision => decide(activeContext, policy),
    getState: (): AuthState => ({
      isAuthenticated: activeContext !== null,
      user: activeContext ? user : null,
      tenant: activeContext?.kind === 'actor' || activeContext?.kind === 'subject'
        ? { id: activeContext.tenantId, name: 'Tenant 42' }
        : null,
      loading: false,
      dataScopes: [],
    }),
  };
}

function decide(
  activeContext: VerifiedAuthContext | null,
  policy: AuthRoutePolicy,
): AuthRouteDecision {
  if (!activeContext) {
    return { allowed: false, reason: 'anonymous' };
  }
  if (!policy.allowedContexts.includes(activeContext.kind)) {
    return { allowed: false, reason: 'context_mismatch' };
  }
  const hasTenant = activeContext.kind === 'actor' || activeContext.kind === 'subject';
  if (policy.tenantContext === 'forbidden' && hasTenant) {
    return { allowed: false, reason: 'tenant_forbidden' };
  }
  if (policy.tenantContext === 'required' && !hasTenant) {
    return { allowed: false, reason: 'tenant_required' };
  }
  return { allowed: true, reason: 'allowed' };
}

function platformContext(): VerifiedAuthContext {
  return {
    kind: 'platform',
    subjectId: '1001',
    sessionId: 'platform-session',
    permissions: ['platform:tenant:read'],
  };
}

function actorContext(): VerifiedAuthContext {
  return {
    kind: 'actor',
    subjectId: '1001',
    sessionId: 'actor-session',
    tenantId: '42',
    permissions: ['tenant:read'],
  };
}
