import { describe, expect, it, vi } from 'vitest';
import { AuthCapabilityImpl } from './AuthCapabilityImpl';
import { createBffSessionAuthState } from './BffSessionAuthState';
import { createBffTenantCapability } from './BffTenantCapability';

const SESSION = {
  authenticated: true,
  csrfToken: 'csrf-1',
  identity: { id: '21', email: 'staff@example.test', username: 'staff' },
  context: { kind: 'actor', tenantId: '42', tenantName: 'Clinic', contextId: 'ctx-1', memberType: 'MEMBER' },
  permissions: ['tenant:profile:read'],
  contexts: [
    { contextId: 'ctx-1', memberId: '7', tenantId: '42', tenantCode: 'clinic', tenantName: 'Clinic', memberType: 'MEMBER', current: true },
    { contextId: 'ctx-2', memberId: '9', tenantId: '43', tenantCode: 'hospital', tenantName: 'Hospital', memberType: 'OWNER', current: false },
  ],
  authentication: { acr: 'brix:aal2', amr: ['pwd', 'otp'], authTime: '2026-10-23T09:00:00Z' },
  expiresAt: '2026-10-23T21:00:00Z',
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('createBffSessionAuthState', () => {
  it('takes identity, context and permissions from the BFF session and holds no token', async () => {
    const fetchFn = vi.fn(async () => json(SESSION));
    const state = createBffSessionAuthState({ fetchFn, navigate: vi.fn() });
    const auth = new AuthCapabilityImpl(state);

    expect(await state.load()).toBe(true);

    expect(fetchFn).toHaveBeenCalledWith('/bff/session', expect.objectContaining({ credentials: 'same-origin' }));
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.getToken()).toBeNull();
    expect(auth.getVerifiedActorContext()).toMatchObject({ kind: 'actor', tenantId: '42', subjectId: '21' });
    expect(auth.hasPermission('tenant:profile:read')).toBe(true);
    expect(auth.canAccessRoute({
      allowedContexts: ['actor'],
      tenantContext: 'required',
      permissions: ['tenant:member:read'],
    })).toMatchObject({ allowed: false, reason: 'permission_denied' });
    expect(state.csrfToken()).toBe('csrf-1');
  });

  it('is anonymous without a session or when the BFF cannot be reached', async () => {
    for (const fetchFn of [vi.fn(async () => json({ authenticated: false })), vi.fn(async () => { throw new Error('offline'); })]) {
      const state = createBffSessionAuthState({ fetchFn, navigate: vi.fn() });
      expect(await state.load()).toBe(false);
      expect(new AuthCapabilityImpl(state).isAuthenticated()).toBe(false);
      expect(state.csrfToken()).toBeNull();
    }
  });

  it('only accepts an Actor context with a tenant', async () => {
    const state = createBffSessionAuthState({
      fetchFn: vi.fn(async () => json({ ...SESSION, context: { kind: 'platform' } })),
      navigate: vi.fn(),
    });
    expect(await state.load()).toBe(false);
  });

  it('logs out through the BFF with the CSRF token and sends logins to the BFF', async () => {
    const fetchFn = vi.fn(async () => json(SESSION));
    const navigate = vi.fn();
    const state = createBffSessionAuthState({ fetchFn, navigate });
    await state.load();

    await state.logout?.();
    state.redirectToLogin('/tenant/members?page=2');

    expect(fetchFn).toHaveBeenLastCalledWith('/bff/logout', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'X-Brix-CSRF': 'csrf-1' }),
    }));
    expect(new AuthCapabilityImpl(state).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/bff/login?returnTo=%2Ftenant%2Fmembers%3Fpage%3D2');
  });

  it('switches contexts through the BFF and only follows same-origin redirects', async () => {
    const origin = 'https://console.example.test';
    const fetchFn = vi.fn(async (input: RequestInfo | URL) => String(input) === '/bff/session'
      ? json(SESSION)
      : json({ redirect: `${origin}/oauth2/authorize?prompt=none` }));
    const navigate = vi.fn();
    const state = createBffSessionAuthState({ fetchFn, navigate, origin });
    await state.load();

    await state.switchContext('ctx-2', '/tenant');

    expect(fetchFn).toHaveBeenLastCalledWith('/bff/switch', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'X-Brix-CSRF': 'csrf-1' }),
      body: JSON.stringify({ contextId: 'ctx-2', returnTo: '/tenant' }),
    }));
    expect(navigate).toHaveBeenCalledWith(`${origin}/oauth2/authorize?prompt=none`);

    fetchFn.mockImplementation(async () => json({ redirect: 'https://attacker.example/x' }));
    await expect(state.switchContext('ctx-2', '/tenant')).rejects.toThrow('context_switch_failed');
    fetchFn.mockImplementation(async () => json({ error: 'context_not_available' }, 403));
    await expect(state.switchContext('ctx-x', '/tenant')).rejects.toThrow('context_not_available');
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('exposes the current and available Actor contexts as the tenant capability', async () => {
    const fetchFn = vi.fn(async () => json(SESSION));
    const state = createBffSessionAuthState({ fetchFn, navigate: vi.fn() });
    const tenants = createBffTenantCapability(state, () => '/tenant');
    const changes = vi.fn();
    tenants.onTenantChange(changes);

    await state.load();

    expect(tenants.getCurrentTenantId()).toBe('42');
    expect(tenants.getCurrentContext?.()).toMatchObject({ role: 'actor', contextId: 'ctx-1', refId: '7', roleType: 'MEMBER' });
    expect(tenants.getAvailableContexts?.().map((context) => context.tenant.name)).toEqual(['Clinic', 'Hospital']);
    expect(changes).toHaveBeenCalledWith(expect.objectContaining({ tenantId: '42' }));
  });
});
