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
