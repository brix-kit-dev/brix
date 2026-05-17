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
 * @file Page Assembly Factory
 * @description Pre-assembled page factory functions for Host configuration-driven page creation
 * @module @brix-sdk/platform-frame-web/pages/factories/pageAssembly
 * @version 3.2.0
 *
 * [Architecture Position]
 * This module belongs to Shell layer (Layer 2.5), providing pre-assembled page
 * factory functions. Host layer only passes configuration to create page components,
 * following the ultra-thin Host principle.
 *
 * [Design Principles]
 * - Factory Pattern: Each factory function encapsulates the configuration required
 *   to create a specific page component
 * - Configuration-Driven: Host provides auth/navigation/branding config, Shell assembles
 * - Zero Business Logic: Factories only wire configuration to page creators
 * - Composable: Individual factories or `createAllPages` convenience function
 *
 * [Dependency Chain]
 * ```
 * Host (config only)
 *   → pageAssembly (this module, Shell layer)
 *     → createSimpleLoginPage (@brix-sdk/platform-auth-web)
 *     → createSimpleDashboardPage (../DashboardPage)
 *     → createSimpleNotFoundPage (../ErrorPages)
 * ```
 *
 * [Usage Example]
 * ```tsx
 * // In Host App.tsx (configuration only)
 * import { createAllPages } from '@brix-sdk/platform-frame-web';
 *
 * const pages = createAllPages({
 *   authService,
 *   navigationService,
 *   branding: hostConfig.branding,
 *   socialProviders: hostConfig.socialProviders,
 * });
 *
 * return (
 *   <Routes>
 *     <Route path="/login" element={<pages.LoginPage />} />
 *     <Route path="/dashboard" element={<pages.DashboardPage />} />
 *   </Routes>
 * );
 * ```
 *
 * @since 3.2.0 Migrated from host-standalone-web to Shell layer (R6.4 architecture fix)
 */

import {
  createSimpleLoginPage,
  createSimpleRegisterPage,
} from '@brix-sdk/platform-auth-web';
import type {
  LoginFormBranding,
  LoginFormResult,
  RegisterFormResult,
} from '@brix-sdk/platform-auth-web';
import { createSimpleDashboardPage } from '../DashboardPage';
import {
  createSimpleNotFoundPage,
  createSimpleUnauthorizedPage,
} from '../ErrorPages';

// ============================================================================
// Types
// ============================================================================

/**
 * Navigation service interface for page factories.
 *
 * Abstraction for programmatic navigation, decoupled from any specific
 * router implementation (React Router, TanStack Router, etc.).
 *
 * @since 3.2.0
 */
export interface PageNavigationService {
  /**
   * Navigate to a specified path.
   *
   * @param path - Target route path
   * @param options - Navigation options
   */
  navigate: (path: string, options?: { replace?: boolean }) => void;
}

/**
 * Auth service interface consumed by page factories.
 *
 * Represents the minimal subset of AuthService needed for page assembly.
 * Host injects the concrete implementation (e.g., from @brix-sdk/platform-auth-web).
 *
 * @since 3.2.0
 */
export interface PageAuthService {
  /** Authenticate a user with username and password */
  login: (username: string, password: string, rememberMe?: boolean) => Promise<LoginFormResult>;
  /** Log out the current user */
  logout: () => void;
  /** Register a new user */
  register: (data: unknown) => Promise<void>;
  /** Retrieve the current authenticated user info */
  getUser: () => { name?: string } | null;
  /** Initiate an OAuth login flow for the given provider */
  initiateOAuthLogin: (providerId: string) => void;
  /** Handle the OAuth callback after redirect */
  handleOAuthCallback: (provider: string, code: string, state?: string) => Promise<void>;
  /** Check whether the current session is authenticated */
  isAuthenticated: () => boolean;
}

/**
 * Social login provider configuration.
 *
 * Describes a social identity provider available for login/registration.
 *
 * @since 3.2.0
 */
export interface PageSocialProvider {
  /** Provider unique identifier (e.g., 'google', 'wechat') */
  id: string;
  /** Display name shown to users */
  name: string;
  /** Optional icon identifier or URL */
  icon?: string;
}

/**
 * Dependencies required by page factory functions.
 *
 * Host layer provides these dependencies when calling factory functions.
 * All fields represent configuration or service references — no business
 * logic is injected.
 *
 * @since 3.2.0
 */
export interface PageFactoryDeps {
  /** Auth service for login/register/logout operations */
  authService: PageAuthService;
  /** Navigation service for programmatic routing */
  navigationService: PageNavigationService;
  /** Branding config applied to login/register/error pages */
  branding: LoginFormBranding;
  /** Optional social login providers to display on auth pages */
  socialProviders?: PageSocialProvider[];
}

/**
 * Result type returned by `createAllPages`.
 *
 * Contains all pre-assembled page components ready for route mounting.
 *
 * @since 3.2.0
 */
export interface AssembledPages {
  /** Login page component */
  LoginPage: React.ComponentType;
  /** Registration page component */
  RegisterPage: React.ComponentType;
  /** Dashboard (home) page component */
  DashboardPage: React.ComponentType;
  /** 404 Not Found page component */
  NotFoundPage: React.ComponentType;
  /** 403 Unauthorized page component */
  UnauthorizedPage: React.ComponentType;
}

// ============================================================================
// Individual Page Factory Functions
// ============================================================================

/**
 * Create the login page component.
 *
 * Configures the login page with authentication callbacks, branding,
 * and social login providers. The resulting component is stateless and
 * ready for direct route mounting.
 *
 * @param deps - Page factory dependencies injected by Host
 * @returns Configured login page React component
 *
 * @example
 * ```tsx
 * const LoginPage = createLoginPageFactory({
 *   authService, navigationService, branding
 * });
 * <Route path="/login" element={<LoginPage />} />
 * ```
 *
 * @since 3.2.0
 */
export function createLoginPageFactory(deps: PageFactoryDeps): React.ComponentType {
  const { authService, navigationService, branding, socialProviders = [] } = deps;

  return createSimpleLoginPage({
    onLogin: async (data: { username: string; password: string; rememberMe?: boolean }): Promise<LoginFormResult> => {
      try {
        return await authService.login(data.username, data.password, data.rememberMe);
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : 'Login failed',
        };
      }
    },
    onLoginSuccess: (result) => navigationService.navigate(
      result.redirectTo ?? '/dashboard',
      { replace: true },
    ),
    onForgotPassword: () => navigationService.navigate('/forgot-password'),
    onRegister: () => navigationService.navigate('/register'),
    branding,
    features: {
      showRememberMe: true,
      showForgotPassword: true,
      showRegisterLink: true,
      enableSocialLogin: socialProviders.length > 0,
    },
    socialProviders,
    onSocialLogin: (providerId: string) => {
      authService.initiateOAuthLogin(providerId);
    },
  });
}

/**
 * Create the registration page component.
 *
 * Configures the registration page with user registration callbacks and branding.
 *
 * @param deps - Page factory dependencies injected by Host
 * @returns Configured registration page React component
 *
 * @example
 * ```tsx
 * const RegisterPage = createRegisterPageFactory({
 *   authService, navigationService, branding
 * });
 * <Route path="/register" element={<RegisterPage />} />
 * ```
 *
 * @since 3.2.0
 */
export function createRegisterPageFactory(deps: PageFactoryDeps): React.ComponentType {
  const { authService, navigationService, branding } = deps;

  return createSimpleRegisterPage({
    onRegister: async (data: Record<string, unknown>): Promise<RegisterFormResult> => {
      try {
        await authService.register(data);
        return { success: true };
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : 'Registration failed',
        };
      }
    },
    onRegisterSuccess: () => {
      navigationService.navigate('/login', { replace: true });
    },
    onBackToLogin: () => navigationService.navigate('/login'),
    branding,
    features: {
      showTermsCheckbox: true,
      termsUrl: '/terms',
      privacyUrl: '/privacy',
    },
  });
}

/**
 * Create the dashboard page component.
 *
 * Configures the main dashboard page with user information and navigation.
 *
 * @param deps - Page factory dependencies injected by Host
 * @returns Configured dashboard page React component
 *
 * @example
 * ```tsx
 * const DashboardPage = createDashboardPageFactory({
 *   authService, navigationService, branding
 * });
 * <Route path="/dashboard" element={<DashboardPage />} />
 * ```
 *
 * @since 3.2.0
 */
export function createDashboardPageFactory(deps: PageFactoryDeps): React.ComponentType {
  const { authService, navigationService, branding } = deps;

  return createSimpleDashboardPage({
    branding,
    username: authService.getUser()?.name || 'User',
    onNavigate: navigationService.navigate,
  });
}

/**
 * Create error page components (404 Not Found and 403 Unauthorized).
 *
 * Configures error pages with navigation callbacks and branding for
 * consistent error handling across the application.
 *
 * @param deps - Page factory dependencies injected by Host
 * @returns Object containing NotFoundPage and UnauthorizedPage components
 *
 * @since 3.2.0
 */
export function createErrorPagesFactory(deps: PageFactoryDeps): {
  NotFoundPage: React.ComponentType;
  UnauthorizedPage: React.ComponentType;
} {
  const { navigationService, branding } = deps;

  const NotFoundPage = createSimpleNotFoundPage({
    branding,
    onGoHome: () => navigationService.navigate('/dashboard'),
    onGoBack: () => window.history.back(),
  });

  const UnauthorizedPage = createSimpleUnauthorizedPage({
    branding,
    onGoHome: () => navigationService.navigate('/dashboard'),
    onReLogin: () => navigationService.navigate('/login'),
  });

  return { NotFoundPage, UnauthorizedPage };
}

// ============================================================================
// Convenience: Create All Pages at Once
// ============================================================================

/**
 * Create all standard page components at once.
 *
 * Convenience function that creates all page components with consistent
 * dependencies and configuration. This is the recommended entry point
 * for Host layer page assembly.
 *
 * @param deps - Page factory dependencies injected by Host
 * @returns Object containing all assembled page components
 *
 * @example
 * ```tsx
 * // Host App.tsx — pure configuration, no business logic
 * const pages = createAllPages({
 *   authService,
 *   navigationService,
 *   branding: hostConfig.branding,
 *   socialProviders: regionalSocialProviders.international,
 * });
 *
 * return (
 *   <Routes>
 *     <Route path="/login" element={<pages.LoginPage />} />
 *     <Route path="/register" element={<pages.RegisterPage />} />
 *     <Route path="/dashboard" element={<pages.DashboardPage />} />
 *     <Route path="/403" element={<pages.UnauthorizedPage />} />
 *     <Route path="/404" element={<pages.NotFoundPage />} />
 *   </Routes>
 * );
 * ```
 *
 * @since 3.2.0
 */
export function createAllPages(deps: PageFactoryDeps): AssembledPages {
  const LoginPage = createLoginPageFactory(deps);
  const RegisterPage = createRegisterPageFactory(deps);
  const DashboardPage = createDashboardPageFactory(deps);
  const { NotFoundPage, UnauthorizedPage } = createErrorPagesFactory(deps);

  return {
    LoginPage,
    RegisterPage,
    DashboardPage,
    NotFoundPage,
    UnauthorizedPage,
  };
}
