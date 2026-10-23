/**
 * @file BFF tenant capability
 * @module @brix-sdk/platform-auth-web/BffTenantCapability
 *
 * TenantCapability of a staff console behind the BFF: the current Actor context and the
 * identity's other Actor contexts come from the BFF session (multi-tenant 5.1
 * BRX-TENANT-UI-002); switching goes through the BFF (identity-auth BRX-IA-FLOW-004) and
 * reloads the console, which clears every cache and page state of the previous context.
 *
 * @since 3.2.0
 */

import type {
  ActorTenantAccessContext,
  TenantCapability,
  TenantChangeListener,
  TenantInfo,
} from '@brix-sdk/runtime-sdk-api-web';
import type { BffContextOption, BffSessionAuthState } from './BffSessionAuthState';

export function createBffTenantCapability(
  session: BffSessionAuthState,
  currentPath: () => string = () => window.location.pathname + window.location.search,
): TenantCapability {
  const listeners = new Set<TenantChangeListener>();
  let previousTenantId: string | null = null;

  function contexts(): readonly ActorTenantAccessContext[] {
    const user = session.getAuthState().user;
    return session.contexts().map((option) => toContext(option, user?.id ?? '', user?.displayName ?? user?.username ?? ''));
  }

  function current(): ActorTenantAccessContext | null {
    return contexts().find((context) => context.metadata?.current === true) ?? null;
  }

  session.subscribeAuthChange(() => {
    const tenant = current()?.tenant ?? null;
    const event = {
      tenantId: tenant?.id ?? null,
      previousTenantId,
      tenant,
      context: current(),
      timestamp: Date.now(),
    };
    previousTenantId = tenant?.id ?? null;
    for (const listener of listeners) {
      listener(event);
    }
  });

  return {
    getCurrentTenantId: () => current()?.tenantId ?? null,
    getCurrentTenant: () => current()?.tenant ?? null,
    getCurrentContext: () => current(),
    getAvailableTenants: () => contexts().map((context) => context.tenant),
    getAvailableContexts: () => contexts(),
    isFeatureEnabled: () => false,
    async switchTenant(tenantId: string): Promise<void> {
      const target = contexts().find((context) => context.tenantId === tenantId);
      if (!target) {
        throw new Error('context_not_available');
      }
      await session.switchContext(target.contextId, currentPath());
    },
    switchContext: (contextId: string) => session.switchContext(contextId, currentPath()),
    onTenantChange(listener: TenantChangeListener): () => void {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

function toContext(option: BffContextOption, userId: string, displayName: string): ActorTenantAccessContext {
  const tenant: TenantInfo = { id: option.tenantId, code: option.tenantCode, name: option.tenantName, status: 'ACTIVE' };
  return {
    role: 'actor',
    contextId: option.contextId,
    tenantId: option.tenantId,
    tenant,
    roleType: option.memberType,
    refId: option.memberId,
    userId,
    displayName,
    metadata: { current: option.current },
  };
}
