import { describe, expect, it, vi } from 'vitest';
import { AuthCapabilityImpl } from './AuthCapabilityImpl';
import { createBffSessionAuthState } from './BffSessionAuthState';

const SESSION = {
  authenticated: true,
  csrfToken: 'csrf-1',
  identity: { id: '21', email: 'staff@example.test', username: 'staff' },
  context: { kind: 'actor', tenantId: '42', tenantName: 'Clinic', contextId: 'ctx-1', memberType: 'MEMBER' },
  permissions: ['tenant:profile:read'],
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
});
