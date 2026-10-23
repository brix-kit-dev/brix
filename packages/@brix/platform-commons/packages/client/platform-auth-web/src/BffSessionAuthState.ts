/**
 * @file BFF session auth state
 * @module @brix-sdk/platform-auth-web/BffSessionAuthState
 *
 * Auth state of a console that runs behind the BFF (root BRX-RS-SECURITY-012, frontend
 * BRX-FE-AUTH-002/003). The session (identity, context, permissions, authentication level,
 * expiry) comes only from the BFF session endpoint; the browser holds the HttpOnly session
 * cookie and never an access or refresh token, so `token` stays `null`.
 *
 * @since 3.2.0
 */

import type {
  AuthChangeHandler,
  AuthChangeEvent,
  AuthState,
  InternalAuthState,
  LoginResult,
  User,
  VerifiedActorContext,
} from '@brix-sdk/runtime-sdk-api-web';
import type { AuthCapabilityConfig } from './AuthCapabilityImpl';

/** An Actor context of the signed-in identity, as the BFF session lists it. */
export interface BffContextOption {
  readonly contextId: string;
  readonly memberId: string;
  readonly tenantId: string;
  readonly tenantCode: string;
  readonly tenantName: string;
  readonly memberType: string;
  readonly current: boolean;
}

export interface BffSessionAuthStateOptions {
  /** @default '/bff/session' */
  readonly sessionUrl?: string;
  /** @default '/bff/switch' */
  readonly switchUrl?: string;
  /** @default '/bff/login' */
  readonly loginUrl?: string;
  /** @default '/bff/logout' */
  readonly logoutUrl?: string;
  /** @default 'X-Brix-CSRF' */
  readonly csrfHeaderName?: string;
  readonly fetchFn?: typeof fetch;
  /** Full-page navigation; defaults to `window.location.assign`. */
  readonly navigate?: (url: string) => void;
  /** This console's origin; redirects elsewhere are refused. Defaults to `window.location.origin`. */
  readonly origin?: string;
}

export interface BffSessionAuthState extends AuthCapabilityConfig {
  /** Loads the session from the BFF; resolves to whether there is one. */
  load(): Promise<boolean>;
  /** CSRF token of the current session for state-changing API requests. */
  csrfToken(): string | null;
  /** Sends the browser to the BFF login; it returns to `returnTo` on this console afterwards. */
  redirectToLogin(returnTo: string): void;
  /** The identity's Actor contexts, the current one included. */
  contexts(): readonly BffContextOption[];
  /**
   * Switches to another Actor context of the same identity through the BFF (multi-tenant T-26):
   * the browser leaves for a silent re-authorization and comes back to `returnTo` with a new
   * session, so no state of the previous context survives.
   */
  switchContext(contextId: string, returnTo: string): Promise<void>;
}

interface BffSessionBody {
  readonly authenticated?: boolean;
  readonly csrfToken?: string;
  readonly identity?: { readonly id?: string; readonly email?: string; readonly username?: string };
  readonly context?: {
    readonly kind?: string;
    readonly tenantId?: string;
    readonly tenantName?: string;
    readonly contextId?: string;
    readonly memberType?: string;
  };
  readonly permissions?: readonly string[];
  readonly contexts?: readonly Partial<BffContextOption>[];
  readonly authentication?: { readonly acr?: string; readonly amr?: readonly string[]; readonly authTime?: string };
  readonly expiresAt?: string;
}

const ANONYMOUS: InternalAuthState = {
  user: null,
  token: null,
  tenant: null,
  featureFlags: {},
  dataScopes: [],
  loading: false,
  activeContext: null,
  sessionState: 'anonymous',
};

export function createBffSessionAuthState(options: BffSessionAuthStateOptions = {}): BffSessionAuthState {
  const sessionUrl = options.sessionUrl ?? '/bff/session';
  const switchUrl = options.switchUrl ?? '/bff/switch';
  const loginUrl = options.loginUrl ?? '/bff/login';
  const logoutUrl = options.logoutUrl ?? '/bff/logout';
  const csrfHeaderName = options.csrfHeaderName ?? 'X-Brix-CSRF';
  const fetchFn = options.fetchFn ?? ((input: RequestInfo | URL, init?: RequestInit) => fetch(input, init));
  const navigate = options.navigate ?? ((url: string) => window.location.assign(url));
  const handlers = new Set<AuthChangeHandler>();
  let state: InternalAuthState = { ...ANONYMOUS, loading: true };
  let csrf: string | null = null;
  let contexts: readonly BffContextOption[] = [];

  function publish(next: InternalAuthState): void {
    const previous = state;
    state = next;
    const event: AuthChangeEvent = {
      type: next.sessionState === 'authenticated' ? 'login' : 'logout',
      user: next.user,
      state: toAuthState(next),
      previousState: toAuthState(previous),
      timestamp: Date.now(),
    };
    for (const handler of handlers) {
      handler(event);
    }
  }

  function redirectToLogin(returnTo: string): void {
    navigate(`${loginUrl}?returnTo=${encodeURIComponent(returnTo)}`);
  }

  return {
    getAuthState: () => state,
    subscribeAuthChange: (handler) => {
      handlers.add(handler);
      return () => {
        handlers.delete(handler);
      };
    },
    async load(): Promise<boolean> {
      let body: BffSessionBody | null = null;
      try {
        const response = await fetchFn(sessionUrl, {
          method: 'GET',
          credentials: 'same-origin',
          cache: 'no-store',
          headers: { Accept: 'application/json' },
        });
        body = response.ok ? (await response.json()) as BffSessionBody : null;
      } catch {
        body = null;
      }
      const next = body?.authenticated === true ? fromSession(body) : null;
      csrf = next ? readText(body?.csrfToken) ?? null : null;
      contexts = next ? readContexts(body?.contexts) : [];
      publish(next ?? ANONYMOUS);
      return next !== null;
    },
    csrfToken: () => csrf,
    redirectToLogin,
    contexts: () => contexts,
    async switchContext(contextId: string, returnTo: string): Promise<void> {
      const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' };
      if (csrf) {
        headers[csrfHeaderName] = csrf;
      }
      const response = await fetchFn(switchUrl, {
        method: 'POST',
        credentials: 'same-origin',
        headers,
        body: JSON.stringify({ contextId, returnTo }),
      });
      const body = (await response.json().catch(() => null)) as { redirect?: unknown; error?: unknown } | null;
      const origin = options.origin ?? window.location.origin;
      if (!response.ok || typeof body?.redirect !== 'string' || !body.redirect.startsWith(`${origin}/`)) {
        throw new Error(typeof body?.error === 'string' ? body.error : 'context_switch_failed');
      }
      navigate(body.redirect);
    },
    login: async (): Promise<LoginResult> => {
      redirectToLogin(window.location.pathname + window.location.search);
      return { success: false, errorCode: 'auth.redirect' };
    },
    logout: async (): Promise<void> => {
      const headers: Record<string, string> = { Accept: 'application/json' };
      if (csrf) {
        headers[csrfHeaderName] = csrf;
      }
      try {
        await fetchFn(logoutUrl, { method: 'POST', credentials: 'same-origin', headers });
      } finally {
        csrf = null;
        publish(ANONYMOUS);
      }
    },
  };
}

function fromSession(body: BffSessionBody): InternalAuthState | null {
  const subjectId = readText(body.identity?.id);
  const tenantId = readText(body.context?.tenantId);
  if (!subjectId || body.context?.kind !== 'actor' || !tenantId) {
    return null;
  }
  const permissions = (body.permissions ?? []).filter(
    (permission): permission is string => typeof permission === 'string' && permission.length > 0,
  );
  const memberType = readText(body.context?.memberType);
  const username = readText(body.identity?.username) ?? readText(body.identity?.email) ?? subjectId;
  const user: User = {
    id: subjectId,
    username,
    email: readText(body.identity?.email),
    displayName: username,
    roles: memberType ? [memberType] : [],
    permissions: [...permissions],
  };
  const activeContext: VerifiedActorContext = {
    kind: 'actor',
    subjectId,
    sessionId: readText(body.context?.contextId) ?? subjectId,
    tenantId,
    permissions,
    expiresAt: readText(body.expiresAt),
  };
  return {
    user,
    token: null,
    tenant: { id: tenantId, name: readText(body.context?.tenantName) ?? tenantId },
    featureFlags: {},
    dataScopes: [],
    loading: false,
    activeContext,
    sessionState: 'authenticated',
  };
}

function readContexts(value: readonly Partial<BffContextOption>[] | undefined): readonly BffContextOption[] {
  return (value ?? []).flatMap((option) => {
    const contextId = readText(option.contextId);
    const tenantId = readText(option.tenantId);
    if (!contextId || !tenantId) {
      return [];
    }
    return [{
      contextId,
      tenantId,
      memberId: readText(option.memberId) ?? '',
      tenantCode: readText(option.tenantCode) ?? tenantId,
      tenantName: readText(option.tenantName) ?? tenantId,
      memberType: readText(option.memberType) ?? '',
      current: option.current === true,
    }];
  });
}

function toAuthState(internal: InternalAuthState): AuthState {
  return {
    isAuthenticated: internal.sessionState === 'authenticated' && internal.user !== null,
    user: internal.user,
    tenant: internal.tenant,
    loading: internal.loading,
    dataScopes: internal.dataScopes,
  };
}

function readText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}
