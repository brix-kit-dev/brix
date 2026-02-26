/**
 * @file 认证能力实现
 * @description 实现 AuthCapability 接口（v3.0 只读版）
 * @module @brix/platform-auth-web/AuthCapabilityImpl
 * @version 3.0.0
 * 
 * 【架构说明】
 * AuthCapabilityImpl 是 AuthCapability 接口的实现，
 * 提供只读的认证状态访问和权限检查能力。
 * 
 * 【v3.0 设计原则】
 * 1. 插件只能读取认证状态，不能修改
 * 2. 登录/登出由 Host 负责
 * 3. Token 管理完全由 Host 处理
 * 
 * 【核心职责】
 * 1. 提供当前用户信息
 * 2. 检查用户权限和角色
 * 3. 提供认证状态变化订阅
 * 4. 提供 Token 用于 API 调用
 * 
 * 【架构红线】
 * 插件禁止调用登录/登出方法
 * 插件禁止直接操作 Token
 * 插件禁止缓存用户信息
 * 插件只能通过 AuthCapability 读取认证状态
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
} from '@brix/runtime-sdk-api-web';

/**
 * 认证状态变化处理器
 */
export type AuthChangeHandler = (event: AuthChangeEvent) => void;

/**
 * 内部认证状态存储
 * 
 * 由 Host 管理，AuthCapabilityImpl 只读
 */
export interface InternalAuthState {
  /**
   * 当前用户
   */
  user: User | null;
  
  /**
   * 访问 Token
   */
  token: string | null;
  
  /**
   * 当前租户
   */
  tenant: Tenant | null;
  
  /**
   * Feature Flags
   */
  featureFlags: Record<string, boolean>;
  
  /**
   * 数据范围列表
   */
  dataScopes: DataScope[];
  
  /**
   * 是否正在加载
   */
  loading: boolean;
}

/**
 * 认证能力配置
 */
export interface AuthCapabilityConfig {
  /**
   * 获取认证状态的函数
   * 
   * 由 Host 提供，返回当前认证状态
   */
  getAuthState: () => InternalAuthState;
  
  /**
   * 订阅认证状态变化
   * 
   * 由 Host 提供，用于订阅状态变化事件
   */
  subscribeAuthChange: (handler: AuthChangeHandler) => Unsubscribe;
  
  /**
   * 登录函数（可选）
   * 
   * 由 Host 提供，用于处理登录
   */
  login?: (credentials: LoginCredentials) => Promise<LoginResult>;
  
  /**
   * 登出函数（可选）
   * 
   * 由 Host 提供，用于处理登出
   */
  logout?: () => Promise<void>;
  
  /**
   * 刷新 Token 函数（可选）
   * 
   * 由 Host 提供，用于刷新 Token
   */
  refreshToken?: () => Promise<string>;
}

/**
 * 认证能力实现
 * 
 * 实现 AuthCapability 接口，提供只读的认证能力
 * 
 * 【使用示例】
 * ```typescript
 * // Host 初始化时创建
 * const authCapability = new AuthCapabilityImpl({
 *   getAuthState: () => authStore.getState(),
 *   subscribeAuthChange: authStore.subscribe,
 * });
 * 
 * // 插件使用
 * const user = authCapability.getCurrentUser();
 * const canEdit = authCapability.hasPermission('booking:write');
 * ```
 */
export class AuthCapabilityImpl implements AuthCapability {
  /**
   * 获取认证状态的函数
   */
  private getInternalAuthState: () => InternalAuthState;
  
  /**
   * 订阅认证状态变化的函数
   */
  private subscribeAuthChange: (handler: AuthChangeHandler) => Unsubscribe;
  
  /**
   * 登录函数
   */
  private loginFn?: (credentials: LoginCredentials) => Promise<LoginResult>;
  
  /**
   * 登出函数
   */
  private logoutFn?: () => Promise<void>;
  
  /**
   * 刷新 Token 函数
   */
  private refreshTokenFn?: () => Promise<string>;
  
  /**
   * 订阅取消函数集合（用于销毁时清理）
   */
  private subscriptions: Set<Unsubscribe> = new Set();
  
  /**
   * 构造函数
   * 
   * @param config - 配置对象
   */
  constructor(config: AuthCapabilityConfig) {
    this.getInternalAuthState = config.getAuthState;
    this.subscribeAuthChange = config.subscribeAuthChange;
    this.loginFn = config.login;
    this.logoutFn = config.logout;
    this.refreshTokenFn = config.refreshToken;
  }
  
  /**
   * 获取当前用户
   * 
   * @returns 当前登录用户，未登录时返回 null
   */
  getCurrentUser(): User | null {
    return this.getInternalAuthState().user;
  }
  
  /**
   * 检查是否已登录
   * 
   * @returns 是否已认证
   */
  isAuthenticated(): boolean {
    const state = this.getInternalAuthState();
    return state.user !== null && state.token !== null;
  }
  
  /**
   * 检查是否具有指定权限
   * 
   * @param permission - 权限标识
   * @returns 是否具有权限
   */
  hasPermission(permission: string): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    return user.permissions.includes(permission);
  }
  
  /**
   * 检查是否具有任一指定权限
   * 
   * @param permissions - 权限标识数组
   * @returns 是否具有任一权限
   */
  hasAnyPermission(permissions: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    return permissions.some(perm => user.permissions.includes(perm));
  }
  
  /**
   * 检查是否具有全部指定权限
   * 
   * @param permissions - 权限标识数组
   * @returns 是否具有全部权限
   */
  hasAllPermissions(permissions: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    return permissions.every(perm => user.permissions.includes(perm));
  }
  
  /**
   * 检查是否具有指定角色
   * 
   * @param role - 角色标识
   * @returns 是否具有角色
   */
  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    return user.roles.includes(role);
  }
  
  /**
   * 检查是否具有任一指定角色
   * 
   * @param roles - 角色标识数组
   * @returns 是否具有任一角色
   */
  hasAnyRole(roles: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    return roles.some(role => user.roles.includes(role));
  }
  
  /**
   * 获取访问 Token
   * 
   * @returns 访问 Token，未登录时返回 null
   */
  getToken(): string | null {
    return this.getInternalAuthState().token;
  }
  
  /**
   * 获取当前租户
   * 
   * @returns 当前租户
   */
  getCurrentTenant(): Tenant | null {
    return this.getInternalAuthState().tenant;
  }
  
  /**
   * 获取租户 ID
   * 
   * @returns 租户 ID，如果没有租户返回空字符串
   */
  getTenantId(): string {
    return this.getInternalAuthState().tenant?.id ?? '';
  }
  
  /**
   * 检查是否具有全部指定角色
   * 
   * @param roles - 角色标识数组
   * @returns 是否具有全部角色
   */
  hasAllRoles(roles: string[]): boolean {
    const user = this.getCurrentUser();
    
    if (!user) {
      return false;
    }
    
    return roles.every(role => user.roles.includes(role));
  }
  
  /**
   * 获取数据范围
   * 
   * @param _resource - 资源类型（可选，暂未使用）
   * @returns 数据范围列表
   */
  getDataScopes(_resource?: string): DataScope[] {
    return this.getInternalAuthState().dataScopes;
  }
  
  /**
   * 登录
   * 
   * @param credentials - 登录凭证
   * @returns 登录结果
   * @throws 如果 Host 未提供登录函数
   */
  async login(credentials: LoginCredentials): Promise<LoginResult> {
    if (!this.loginFn) {
      throw new Error('登录功能未配置。在 v3.0 架构中，登录由 Host 负责管理。');
    }
    return this.loginFn(credentials);
  }
  
  /**
   * 登出
   * 
   * @throws 如果 Host 未提供登出函数
   */
  async logout(): Promise<void> {
    if (!this.logoutFn) {
      throw new Error('登出功能未配置。在 v3.0 架构中，登出由 Host 负责管理。');
    }
    return this.logoutFn();
  }
  
  /**
   * 刷新 Token
   * 
   * @returns 新的访问令牌
   * @throws 如果 Host 未提供刷新函数
   */
  async refreshToken(): Promise<string> {
    if (!this.refreshTokenFn) {
      throw new Error('Token 刷新功能未配置。在 v3.0 架构中，Token 管理由 Host 负责。');
    }
    return this.refreshTokenFn();
  }
  
  /**
   * 获取认证状态
   * 
   * @returns 当前认证状态
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
   * 订阅认证状态变化
   * 
   * @param listener - 变化监听器
   * @returns 取消订阅函数
   */
  onAuthChange(listener: (event: AuthChangeEvent) => void): () => void {
    const unsubscribe = this.subscribeAuthChange(listener);
    
    // 记录订阅以便清理
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * 检查 Feature Flag 是否启用
   * 
   * @param featureKey - Feature Flag 键名
   * @returns 是否启用
   */
  isFeatureEnabled(featureKey: string): boolean {
    const { featureFlags } = this.getInternalAuthState();
    return featureFlags[featureKey] ?? false;
  }
  
  /**
   * 销毁能力实例
   */
  destroy(): void {
    // 取消所有订阅
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
  }
}
