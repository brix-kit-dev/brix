/**
 * @file platform-auth-web 模块入口
 * @description Web 端认证能力实现模块 - 实现 AuthCapability 接口
 * @module @brix/platform-auth-web
 * @version 3.2.0
 * 
 * 【模块说明】
 * platform-auth-web 是 AuthCapability 接口的实现模块，
 * 提供只读的认证状态访问和权限检查能力。
 * 
 * 【v3.2 架构重构】
 * 本模块采用"三包分离 + 聚合导出"架构：
 * - @brix/platform-auth-web（本模块）— 核心能力实现（AuthCapabilityImpl）
 * - @brix/platform-auth-ui-web — UI 组件和页面
 * - @brix/platform-auth-service-web — 服务工厂
 * 
 * 本模块作为聚合入口，从子包纯重导出所有内容，保持向后兼容。
 * 新代码建议直接引用对应的子包以获得更好的 tree-shaking 效果。
 * 
 * 【架构位置】
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ 能力契约层（runtime-sdk-api-web）                                       │
 * │ └── AuthCapability 接口定义（v3.0 只读版）                              │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ 能力实现层（platform-commons）                                          │
 * │ └── platform-auth-web（本模块）⭐                                       │
 * │      ├── AuthCapabilityImpl（核心能力实现 — 本模块唯一源码）            │
 * │      ├── 重导出 → @brix/platform-auth-ui-web（UI 组件/Hooks/页面）     │
 * │      └── 重导出 → @brix/platform-auth-service-web（服务工厂）           │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * 【v3.0 设计原则】
 * 1. 插件只能读取认证状态，不能修改
 * 2. 登录/登出由 Host 负责
 * 3. Token 管理完全由 Host 处理
 * 
 * 【架构红线】
 * - 插件禁止调用登录/登出方法
 * - 插件禁止直接操作 Token
 * - 插件禁止缓存用户信息
 * - 插件只能通过 AuthCapability 读取认证状态
 */

// ============================================================================
// 能力实现（核心职责 — 本模块唯一源码）
// ============================================================================

export { 
  AuthCapabilityImpl, 
  type AuthCapabilityConfig, 
  type InternalAuthState, 
  type AuthChangeHandler,
} from './AuthCapabilityImpl';

// ============================================================================
// 重导出 — UI 组件（来源: @brix/platform-auth-ui-web）
// ============================================================================

export { 
  AuthGuard, 
  type AuthGuardProps,
  PermissionGate, 
  type PermissionGateProps,
  LoginForm, 
  type LoginFormProps,
  type LoginFormData,
  type LoginFormResult,
  type LoginFormBranding,
  type LoginFormLabels,
  type LoginFormFeatures,
  type SocialProvider,
  RegisterForm,
  type RegisterFormProps,
  type RegisterFormData,
  type RegisterFormResult,
} from '@brix/platform-auth-ui-web';

// ============================================================================
// 重导出 — Hooks（来源: @brix/platform-auth-ui-web）
// ============================================================================

export { 
  useAuth, 
  type UseAuthResult,
  usePermission, 
  useAnyPermission, 
  useAllPermissions, 
  useRole,
  type UsePermissionResult,
} from '@brix/platform-auth-ui-web';

// ============================================================================
// 重导出 — 预装配页面（来源: @brix/platform-auth-ui-web）
// ============================================================================

export {
  createLoginPage,
  createSimpleLoginPage,
  createSimpleRegisterPage,
  OAuthCallbackPage,
  type OAuthCallbackPageProps,
  type LoginPageConfig,
  type SimpleLoginPageConfig,
  type RegisterPageConfig,
  type AuthService,
  type NavigationService,
  type LoginPageRoutes,
} from '@brix/platform-auth-ui-web';

// ============================================================================
// 重导出 — 服务（来源: @brix/platform-auth-service-web）
// ============================================================================

export {
  createPlatformAuthService,
  type PlatformAuthService,
  type PlatformAuthServiceOptions,
  type AuthUser,
  type LoginResult,
  type RegisterData,
  type RegisterResult,
  type OAuthConfig,
} from '@brix/platform-auth-service-web';

// ============================================================================
// 重导出 — Google OAuth（来源: @brix/platform-auth-service-web）
// ============================================================================

export {
  GoogleOAuthService,
  getGoogleAuthService,
  initGoogleAuth,
  resetGoogleAuth,
  type GoogleOAuthConfig,
  type GoogleAuthResult,
  type GoogleUserInfo,
  type GoogleTokenResponse,
  type OAuthError,
  type GoogleOAuthErrorCode,
  type PKCEPair,
  type TokenExchangeRequest,
  type TokenRefreshRequest,
  type BackendAuthResponse,
} from '@brix/platform-auth-service-web';

