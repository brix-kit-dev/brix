/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file
 * @description AuthCapability v3.0
 * @module @brix-sdk/platform-auth-web/AuthCapabilityImpl
 * @version 3.0.0
 * 
 * AuthCapabilityImpl AuthCapability
 * 3. Token Host
 * 
 * 1.
 * 2. ?
 * 3.
 * 4. Token API
 * 
 */

import type { 
  AuthCapability, 
  User, 
  Tenant,
  AuthChangeEvent,
  DataScope,
  LoginCredentials,
  LoginResult,
  AuthState as IAuthState,
  AuthRouteDecision,
  AuthRoutePolicy,
  VerifiedActorContext,
  VerifiedAuthContext,
  VerifiedBootstrapContext,
  VerifiedPlatformContext,
  VerifiedSession,
  VerifiedSubjectContext,
  Unsubscribe,
  AuthCapabilityConfig,
  InternalAuthState,
  AuthChangeHandler,
} from '@brix-sdk/runtime-sdk-api-web';

// Re-export contract-layer types for backward compatibility
export type { AuthCapabilityConfig, InternalAuthState, AuthChangeHandler };

/**
 * 
 * ```typescript
 * // Host
 * const authCapability = new AuthCapabilityImpl({
 *   getAuthState: () => authStore.getState(),
 *   subscribeAuthChange: authStore.subscribe,
 * });
 * 
 * // ?
 * const user = authCapability.getCurrentUser();
 * const canEdit = authCapability.hasPermission('booking:write');
 * ```
 */
export class AuthCapabilityImpl implements AuthCapability {
  /**
   */
  private getInternalAuthState: () => InternalAuthState;
  
  /**
   */
  private subscribeAuthChange: (handler: AuthChangeHandler) => Unsubscribe;
  
  /**
   */
  private loginFn?: (credentials: LoginCredentials) => Promise<LoginResult>;
  
  /**
   */
  private logoutFn?: () => Promise<void>;
  
  /**
   */
  private refreshTokenFn?: () => Promise<string>;
  
  /**
   */
  private subscriptions: Set<Unsubscribe> = new Set();
  
  /**
   * 
 * @param config -
   */
  constructor(config: AuthCapabilityConfig) {
    this.getInternalAuthState = config.getAuthState;
    this.subscribeAuthChange = config.subscribeAuthChange;
    this.loginFn = config.login;
    this.logoutFn = config.logout;
    this.refreshTokenFn = config.refreshToken;
  }
  
  /**
   * 
 * @returns null
   */
  getCurrentUser(): User | null {
    return this.getInternalAuthState().user;
  }
  
  /**
 * @returns
   */
  isAuthenticated(): boolean {
    const state = this.getInternalAuthState();
    return state.user !== null && state.token !== null;
  }
  
  /**
   * 
 * @param permission -
 * @returns ?
 */
  hasPermission(permission: string): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    if (!Array.isArray(user.permissions)) {
      return false;
    }
    
    return user.permissions.includes(permission);
  }
  
  /**
   * 
 * @param permissions -
 * @returns ?
 */
  hasAnyPermission(permissions: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user || !Array.isArray(user.permissions)) {
      return false;
    }
    
    return permissions.some(perm => user.permissions.includes(perm));
  }
  
  /**
   * 
 * @param permissions -
 * @returns ?
 */
  hasAllPermissions(permissions: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user || !Array.isArray(user.permissions)) {
      return false;
    }
    
    return permissions.every(perm => user.permissions.includes(perm));
  }
  
  /**
   * 
 * @param role -
 * @returns ?
 */
  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    
    if (!user || !Array.isArray(user.roles)) {
      return false;
    }
    
    return user.roles.includes(role);
  }
  
  /**
   * 
 * @param roles -
 * @returns ?
 */
  hasAnyRole(roles: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user || !Array.isArray(user.roles)) {
      return false;
    }
    
    return roles.some(role => user.roles.includes(role));
  }
  
  /**
   * 
 * @returns Token null
   */
  getToken(): string | null {
    return this.getInternalAuthState().token;
  }

  /**
   * Return the provider-verified session snapshot consumed by Route Guards.
   */
  getVerifiedSession(): VerifiedSession {
    const state = this.getInternalAuthState();
    const activeContext = readVerifiedContext(state.activeContext);
    return {
      state: state.sessionState ?? inferSessionState(activeContext),
      activeContext,
      permissions: activeContext?.permissions ?? [],
    };
  }

  /**
   * Return the active provider-verified context without exposing raw tokens.
   */
  getActiveContext(): VerifiedAuthContext | null {
    return this.getVerifiedSession().activeContext;
  }

  /**
   * Return a verified platform context when it is the active context.
   */
  getVerifiedPlatformContext(): VerifiedPlatformContext | null {
    const context = this.getActiveContext();
    return context?.kind === 'platform' ? context : null;
  }

  /**
   * Return a verified tenant actor context when it is the active context.
   */
  getVerifiedActorContext(): VerifiedActorContext | null {
    const context = this.getActiveContext();
    return context?.kind === 'actor' ? context : null;
  }

  /**
   * Return a verified tenant subject context when it is the active context.
   */
  getVerifiedSubjectContext(): VerifiedSubjectContext | null {
    const context = this.getActiveContext();
    return context?.kind === 'subject' ? context : null;
  }

  /**
   * Return a verified bootstrap/setup context when it is the active context.
   */
  getVerifiedBootstrapContext(): VerifiedBootstrapContext | null {
    const context = this.getActiveContext();
    return context?.kind === 'bootstrap-setup' ? context : null;
  }

  /**
   * Evaluate route admission from provider-verified context and permissions.
   */
  canAccessRoute(policy: AuthRoutePolicy): AuthRouteDecision {
    const context = this.getActiveContext();
    if (!context) {
      return { allowed: false, reason: 'anonymous' };
    }
    if (!policy.allowedContexts.includes(context.kind)) {
      return { allowed: false, reason: 'context_mismatch' };
    }

    const hasTenant = hasTenantContext(context);
    if (policy.tenantContext === 'forbidden' && hasTenant) {
      return { allowed: false, reason: 'tenant_forbidden' };
    }
    if (policy.tenantContext === 'required' && !hasTenant) {
      return { allowed: false, reason: 'tenant_required' };
    }

    const requiredPermissions = policy.permissions ?? [];
    if (requiredPermissions.length > 0) {
      const granted = new Set(context.permissions);
      const allowed = policy.requireAllPermissions === false
        ? requiredPermissions.some(permission => granted.has(permission))
        : requiredPermissions.every(permission => granted.has(permission));
      if (!allowed) {
        return { allowed: false, reason: 'permission_denied' };
      }
    }

    return { allowed: true, reason: 'allowed' };
  }
  
  /**
   * 
 * @returns
   */
  getCurrentTenant(): Tenant | null {
    return this.getInternalAuthState().tenant;
  }
  
  /**
   * 
 * @returns IDэ?
 */
  getTenantId(): string {
    return this.getInternalAuthState().tenant?.id ?? '';
  }
  
  /**
   * 
 * @param roles -
 * @returns ?
 */
  hasAllRoles(roles: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user || !Array.isArray(user.roles)) {
      return false;
    }
    
    return roles.every(role => user.roles.includes(role));
  }
  
  /**
   * 
 * @param _resource -
 * @returns
   */
  getDataScopes(_resource?: string): DataScope[] {
    return this.getInternalAuthState().dataScopes;
  }
  
  /**
   * Perform user login.
   * @param credentials - Login credentials
   * @returns Login result
   * @throws Error if login function is not registered by Host
   */
  async login(credentials: LoginCredentials): Promise<LoginResult> {
    if (!this.loginFn) {
      throw new Error('Login function not registered. Per v3.0 architecture, login must be provided by Host.');
    }
    return this.loginFn(credentials);
  }
  
  /**
   * Perform user logout.
   * @throws Error if logout function is not registered by Host
   */
  async logout(): Promise<void> {
    if (!this.logoutFn) {
      throw new Error('Logout function not registered. Per v3.0 architecture, logout must be provided by Host.');
    }
    return this.logoutFn();
  }
  
  /**
   * Refresh the authentication token.
   * @returns New token string
   * @throws Error if refreshToken function is not registered by Host
   */
  async refreshToken(): Promise<string> {
    if (!this.refreshTokenFn) {
      throw new Error('Token refresh function not registered. Per v3.0 architecture, token refresh must be provided by Host.');
    }
    return this.refreshTokenFn();
  }
  
  /**
   * 
 * @returns
   */
  getState(): IAuthState {
    const internal = this.getInternalAuthState();
    return {
      isAuthenticated: this.isAuthenticated(),
      user: internal.user,
      tenant: internal.tenant,
      loading: internal.loading,
      dataScopes: internal.dataScopes,
    };
  }
  
  /**
   * 
 * @param listener -
 * @returns
   */
  onAuthChange(listener: (event: AuthChangeEvent) => void): () => void {
    const unsubscribe = this.subscribeAuthChange(listener);
    
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * 
 * @param featureKey - Feature Flag
 * @returns
   */
  isFeatureEnabled(featureKey: string): boolean {
    const { featureFlags } = this.getInternalAuthState();
    return featureFlags[featureKey] ?? false;
  }
  
  /**
   */
  destroy(): void {
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
  }
}

function inferSessionState(context: VerifiedAuthContext | null): VerifiedSession['state'] {
  if (!context) {
    return 'anonymous';
  }
  return context.kind === 'bootstrap-setup' ? 'challenge' : 'authenticated';
}

function readVerifiedContext(value: VerifiedAuthContext | null | undefined): VerifiedAuthContext | null {
  if (!value || typeof value !== 'object') {
    return null;
  }
  if (!hasText(value.subjectId) || !hasText(value.sessionId) || !Array.isArray(value.permissions)) {
    return null;
  }
  switch (value.kind) {
    case 'platform':
      return value;
    case 'actor':
      return hasText(value.tenantId) ? value : null;
    case 'subject':
      return hasText(value.tenantId) ? value : null;
    case 'bootstrap-setup':
      return value.bootstrapStage === 'setup'
        || value.bootstrapStage === 'bootstrap'
        || value.bootstrapStage === 'mfa-challenge'
        ? value
        : null;
    default:
      return null;
  }
}

function hasTenantContext(context: VerifiedAuthContext): context is VerifiedActorContext | VerifiedSubjectContext {
  return (context.kind === 'actor' || context.kind === 'subject') && hasText(context.tenantId);
}

function hasText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
