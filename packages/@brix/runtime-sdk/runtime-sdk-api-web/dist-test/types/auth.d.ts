/**
 * @file 认证能力类型定义
 * @description 定义认证系统的核心类型，包括用户信息、认证信息、权限验证等
 * @module @brix/runtime-sdk-api-web/types/auth
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件。
 *
 * 【v3.2.0 Phase 1 契约层修复】
 * 补全 AuthCapabilityImpl 依赖的类型：
 * - User: 完整用户信息（包含权限和角色）
 * - Tenant: 租户信息
 * - AuthChangeEvent: 认证状态变化事件
 * - DataScope: 数据范围
 * - LoginCredentials: 登录凭证
 * - LoginResult: 登录结果
 * - AuthState: 认证状态
 */
import type { Unsubscribe } from './event';
/**
 * 认证能力类型标识
 */
export declare const AuthCapabilityType: unique symbol;
/**
 * 基础用户信息
 */
export interface BaseUser {
    /** 用户 ID */
    readonly id: string;
    /** 用户名 */
    readonly username: string;
    /** 邮箱 */
    readonly email?: string;
    /** 显示名称 */
    readonly displayName?: string;
    /** 头像 URL */
    readonly avatar?: string;
    /** 创建时间 */
    readonly createdAt: string;
    /** 更新时间 */
    readonly updatedAt?: string;
}
/**
 * 认证用户信息
 *
 * <p>包含角色和权限信息的用户对象。</p>
 */
export interface AuthUser {
    /** 用户 ID */
    id: string;
    /** 用户名 */
    username: string;
    /** 邮箱 */
    email?: string;
    /** 显示名称 */
    displayName?: string;
    /** 角色列表 */
    roles: string[];
    /** 权限列表 */
    permissions: string[];
}
/**
 * 完整用户信息
 *
 * <p>包含角色、权限和扩展属性的用户对象。
 * 这是 AuthCapability.getCurrentUser() 返回的类型。</p>
 *
 * @since 3.2.0
 */
export interface User {
    /** 用户 ID */
    readonly id: string;
    /** 用户名 */
    readonly username: string;
    /** 邮箱 */
    readonly email?: string;
    /** 显示名称 */
    readonly displayName?: string;
    /** 头像 URL */
    readonly avatar?: string;
    /** 角色列表 */
    readonly roles: string[];
    /** 权限列表 */
    readonly permissions: string[];
    /** 创建时间 */
    readonly createdAt?: string;
    /** 更新时间 */
    readonly updatedAt?: string;
    /** 扩展属性 */
    readonly [key: string]: unknown;
}
/**
 * 租户信息
 *
 * <p>多租户系统中的租户对象。</p>
 *
 * @since 3.2.0
 */
export interface Tenant {
    /** 租户 ID */
    readonly id: string;
    /** 租户名称 */
    readonly name: string;
    /** 租户编码（唯一标识） */
    readonly code?: string;
    /** 租户类型 */
    readonly type?: string;
    /** 租户状态 */
    readonly status?: 'active' | 'inactive' | 'suspended';
    /** 租户配置 */
    readonly config?: Record<string, unknown>;
}
/**
 * 数据范围
 *
 * <p>描述用户的数据访问权限范围。</p>
 *
 * @since 3.2.0
 */
export interface DataScope {
    /** 数据范围 ID */
    readonly id: string;
    /** 范围类型（如 'org', 'dept', 'self' 等） */
    readonly type: string;
    /** 范围值（如组织 ID 列表） */
    readonly value: string | string[];
    /** 资源类型（可选，限定适用的资源） */
    readonly resource?: string;
}
/**
 * 登录凭证
 *
 * <p>用户登录时提交的凭证信息。</p>
 *
 * @since 3.2.0
 */
export interface LoginCredentials {
    /** 用户名或邮箱 */
    readonly username?: string;
    /** 密码 */
    readonly password?: string;
    /** 验证码 */
    readonly captcha?: string;
    /** 记住登录状态 */
    readonly rememberMe?: boolean;
    /** 第三方登录提供商 */
    readonly provider?: string;
    /** 第三方登录授权码 */
    readonly authorizationCode?: string;
    /** 扩展字段 */
    readonly [key: string]: unknown;
}
/**
 * 登录结果
 *
 * <p>登录请求的返回结果。</p>
 *
 * @since 3.2.0
 */
export interface LoginResult {
    /** 是否登录成功 */
    readonly success: boolean;
    /** 用户信息（成功时） */
    readonly user?: User;
    /** 访问令牌（成功时） */
    readonly token?: string;
    /** 刷新令牌（成功时） */
    readonly refreshToken?: string;
    /** 令牌有效期（秒） */
    readonly expiresIn?: number;
    /** 错误代码（失败时） */
    readonly errorCode?: string;
    /** 错误消息（失败时） */
    readonly errorMessage?: string;
    /** 是否需要二次验证 */
    readonly requireMfa?: boolean;
    /** MFA 会话标识 */
    readonly mfaSession?: string;
}
/**
 * 认证状态
 *
 * <p>描述当前认证上下文的完整状态。</p>
 *
 * @since 3.2.0
 */
export interface AuthState {
    /** 是否已认证 */
    readonly isAuthenticated: boolean;
    /** 当前用户 */
    readonly user: User | null;
    /** 当前租户 */
    readonly tenant: Tenant | null;
    /** 是否正在加载 */
    readonly loading: boolean;
    /** 数据范围列表 */
    readonly dataScopes: DataScope[];
}
/**
 * 认证状态变化事件
 *
 * <p>当认证状态发生变化时触发的事件。</p>
 *
 * @since 3.2.0
 */
export interface AuthChangeEvent {
    /** 事件类型 */
    readonly type: 'login' | 'logout' | 'token_refresh' | 'user_update' | 'tenant_switch';
    /** 新的认证状态 */
    readonly state: AuthState;
    /** 之前的认证状态 */
    readonly previousState?: AuthState;
    /** 事件时间戳 */
    readonly timestamp: number;
}
/**
 * 认证信息
 *
 * <p>包含访问令牌和刷新令牌。</p>
 */
export interface AuthInfo {
    /** 访问令牌 */
    readonly accessToken: string;
    /** 刷新令牌 */
    readonly refreshToken?: string;
    /** 令牌有效期（秒） */
    readonly expiresIn: number;
    /** 令牌类型 */
    readonly tokenType: string;
}
/**
 * 认证能力契约
 *
 * <p>为插件提供用户身份验证和权限检查能力。</p>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const auth = context.getCapability<AuthCapability>(AuthCapabilityType);
 *
 * if (auth.isAuthenticated()) {
 *   const user = auth.getCurrentUser();
 *   if (auth.hasPermission('booking:create')) {
 *     // 创建预约
 *   }
 * }
 * ```
 *
 * @since 3.2.0 扩展方法：getCurrentTenant, getTenantId, getDataScopes, getState, onAuthChange
 */
export interface AuthCapability {
    /**
     * 获取当前登录用户
     *
     * @returns 当前用户，未登录时返回 null
     */
    getCurrentUser(): User | null;
    /**
     * 检查是否已认证
     *
     * @returns 是否已登录
     */
    isAuthenticated(): boolean;
    /**
     * 用户登录
     *
     * @param credentials 登录凭据
     * @returns 登录结果
     */
    login(credentials: LoginCredentials): Promise<LoginResult>;
    /**
     * 用户登出
     *
     * @returns Promise，完成时表示登出成功
     */
    logout(): Promise<void>;
    /**
     * 检查是否拥有指定权限
     *
     * @param permission 权限标识
     * @returns 是否拥有权限
     */
    hasPermission(permission: string): boolean;
    /**
     * 检查是否拥有任一指定权限
     *
     * @param permissions 权限标识数组
     * @returns 是否拥有任一权限
     * @since 3.2.0
     */
    hasAnyPermission?(permissions: string[]): boolean;
    /**
     * 检查是否拥有全部指定权限
     *
     * @param permissions 权限标识数组
     * @returns 是否拥有全部权限
     * @since 3.2.0
     */
    hasAllPermissions?(permissions: string[]): boolean;
    /**
     * 检查是否拥有指定角色
     *
     * @param role 角色标识
     * @returns 是否拥有角色
     */
    hasRole(role: string): boolean;
    /**
     * 检查是否拥有任一指定角色
     *
     * @param roles 角色标识数组
     * @returns 是否拥有任一角色
     * @since 3.2.0
     */
    hasAnyRole?(roles: string[]): boolean;
    /**
     * 检查是否拥有全部指定角色
     *
     * @param roles 角色标识数组
     * @returns 是否拥有全部角色
     * @since 3.2.0
     */
    hasAllRoles?(roles: string[]): boolean;
    /**
     * 获取当前访问令牌
     *
     * @returns 访问令牌，未登录时返回 null
     */
    getToken(): string | null;
    /**
     * 获取当前租户
     *
     * @returns 当前租户，未设置时返回 null
     * @since 3.2.0
     */
    getCurrentTenant?(): Tenant | null;
    /**
     * 获取租户 ID
     *
     * @returns 租户 ID，无租户时返回空字符串
     * @since 3.2.0
     */
    getTenantId?(): string;
    /**
     * 获取数据范围
     *
     * @param resource 资源类型（可选）
     * @returns 数据范围列表
     * @since 3.2.0
     */
    getDataScopes?(resource?: string): DataScope[];
    /**
     * 获取认证状态
     *
     * @returns 当前认证状态
     * @since 3.2.0
     */
    getState?(): AuthState;
    /**
     * 订阅认证状态变化
     *
     * @param listener 变化监听器
     * @returns 取消订阅函数
     * @since 3.2.0
     */
    onAuthChange?(listener: (event: AuthChangeEvent) => void): Unsubscribe;
    /**
     * 检查 Feature Flag 是否启用
     *
     * @param featureKey Feature Flag 键名
     * @returns 是否启用
     * @since 3.2.0
     */
    isFeatureEnabled?(featureKey: string): boolean;
    /**
     * 刷新访问令牌
     *
     * @returns 新的访问令牌
     * @since 3.2.0
     */
    refreshToken?(): Promise<string>;
    /**
     * 销毁能力实例
     *
     * @since 3.2.0
     */
    destroy?(): void;
}
//# sourceMappingURL=auth.d.ts.map