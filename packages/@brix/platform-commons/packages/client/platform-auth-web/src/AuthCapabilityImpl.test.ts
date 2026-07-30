import { describe, expect, it } from 'vitest';
import { AuthCapabilityImpl } from './AuthCapabilityImpl';
import type {
  AuthRoutePolicy,
  InternalAuthState,
  User,
  VerifiedAuthContext,
} from '@brix-sdk/runtime-sdk-api-web';

const user: User = {
  id: '1001',
  username: 'platform-admin',
  roles: ['PLATFORM_SUPER_ADMIN'],
  permissions: ['platform:tenant:read', 'platform:admin:read'],
};

describe('AuthCapabilityImpl verified route admission', () => {
  it('allows a verified platform context to enter platform routes without tenant context', () => {
    const auth = capability({
      kind: 'platform',
      subjectId: '1001',
      sessionId: 'platform-session',
      permissions: ['platform:tenant:read'],
    });

    const policy: AuthRoutePolicy = {
      allowedContexts: ['platform'],
      tenantContext: 'forbidden',
      permissions: ['platform:tenant:read'],
    };

    expect(auth.getVerifiedPlatformContext()?.sessionId).toBe('platform-session');
    expect(auth.canAccessRoute(policy)).toEqual({
      allowed: true,
      reason: 'allowed',
    });
  });

  it('denies platform routes to verified actor contexts', () => {
    const auth = capability({
      kind: 'actor',
      subjectId: '1001',
      sessionId: 'actor-session',
      tenantId: '42',
      permissions: ['platform:tenant:read'],
    });

    expect(auth.canAccessRoute({
      allowedContexts: ['platform'],
      tenantContext: 'forbidden',
    })).toEqual({
      allowed: false,
      reason: 'context_mismatch',
    });
  });

  it('denies tenant routes when the verified actor context has no tenant id', () => {
    const auth = capability({
      kind: 'actor',
      subjectId: '1001',
      sessionId: 'actor-session',
      tenantId: '',
      permissions: ['tenant:read'],
    });

    expect(auth.getActiveContext()).toBeNull();
    expect(auth.canAccessRoute({
      allowedContexts: ['actor', 'subject'],
      tenantContext: 'required',
    })).toEqual({
      allowed: false,
      reason: 'anonymous',
    });
  });

  it('denies routes when required permissions are absent', () => {
    const auth = capability({
      kind: 'platform',
      subjectId: '1001',
      sessionId: 'platform-session',
      permissions: ['platform:tenant:read'],
    });

    expect(auth.canAccessRoute({
      allowedContexts: ['platform'],
      tenantContext: 'forbidden',
      permissions: ['platform:admin:revoke'],
    })).toEqual({
      allowed: false,
      reason: 'permission_denied',
    });
  });
});

function capability(activeContext: VerifiedAuthContext): AuthCapabilityImpl {
  const state: InternalAuthState = {
    user,
    token: 'opaque-provider-owned-token',
    tenant: activeContext.kind === 'actor' || activeContext.kind === 'subject'
      ? { id: activeContext.tenantId, name: 'Tenant 42' }
      : null,
    featureFlags: {},
    dataScopes: [],
    loading: false,
    activeContext,
  };
  return new AuthCapabilityImpl({
    getAuthState: () => state,
    subscribeAuthChange: () => () => undefined,
  });
}
