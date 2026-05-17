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
 * @file Platform Frame Web Module Entry
 * @description Web platform frame package - Provides pre-assembled Shell pages, layout, and theme capabilities
 * @module @brix-sdk/platform-frame-web
 * @version 3.3.0
 * 
 * [Module Description]
 * platform-frame-web is the Web platform frame package (Layer 2C), providing:
 * - Pre-assembled Dashboard page
 * - Pre-assembled Error pages (404, 403)
 * - Layout capabilities (LayoutCapability implementation)
 * - Theme capabilities (ThemeCapability implementation)
 * - Layout components (ConsoleLayout, PortalLayout, MinimalLayout)
 * 
 * [Architecture Position]
 * ```text
 * +-------------------------------------------------------------------------+
 * | Capability Layer (platform-commons/client)                              |
 * | +-- platform-auth-web      - Auth capability (LoginPage)                |
 * | +-- platform-frame-web     - Frame + Layout + Theme capabilities        |
 * |      +-- pages/            - Dashboard, Error Pages                     |
 * |      +-- layouts/          - ConsoleLayout, PortalLayout, Minimal       |
 * |      +-- components/       - LayoutContainer, DynamicPluginRoutes       |
 * |      +-- hooks/            - useLayout, useResponsive, useTheme         |
 * |      +-- theme/            - ThemeCapabilityImpl, presets               |
 * +-------------------------------------------------------------------------+
 * | Host Layer (configuration only)                                         |
 * | +-- host-standalone-web    - Configuration + Route mounting             |
 * +-------------------------------------------------------------------------+
 * ```
 * 
 * [Design Principles]
 * 1. After deleting Host, new Host only needs configuration to restore all functionality
 * 2. Pages are pre-assembled by capability layer, Host only injects configuration
 * 3. Layout is fully controlled by Shell, plugins can only request layout changes
 * 4. Follows best practices of Auth0/Firebase/AWS Amplify
 * 
 * [Architectural Constraints]
 * - Plugins are prohibited from directly manipulating document.body
 * - Plugins are prohibited from creating global Portals on body
 * - Plugins are prohibited from modifying global CSS (e.g., overflow)
 * - Plugins can only request layout changes through LayoutCapability
 */

// ============================================================================
// Page factory functions
// ============================================================================

export {
  // Dashboard
  createDashboardPage,
  createSimpleDashboardPage,
  // Error Pages
  createNotFoundPage,
  createSimpleNotFoundPage,
  createUnauthorizedPage,
  createSimpleUnauthorizedPage,
  // Placeholder Page (v3.2.0)
  PlaceholderPage,
  createSimplePlaceholderPage,
  // Page Assembly Factories (v3.2.0 - migrated from Host per R6.4)
  createLoginPageFactory,
  createRegisterPageFactory,
  createDashboardPageFactory,
  createErrorPagesFactory,
  createAllPages,
} from './pages';

// ============================================================================
// Layout Capability Implementation (from platform-layout-web)
// ============================================================================

export { LayoutCapabilityImpl, type LayoutCapabilityConfig } from './LayoutCapabilityImpl';
export { LayoutStore } from './LayoutStore';
export { GovernancePolicyHandler } from './GovernancePolicy';

// ============================================================================
// Layout components
// ============================================================================

export { ConsoleLayout, type ConsoleLayoutProps } from './layouts/ConsoleLayout';
export { PortalLayout, type PortalLayoutProps } from './layouts/PortalLayout';
export { MinimalLayout, type MinimalLayoutProps } from './layouts/MinimalLayout';
export {
  ProtectedLayout,
  type ProtectedLayoutProps,
  type LayoutDimensions,
  type LayoutBranding,
  type LayoutUser,
} from './layouts/ProtectedLayout';

// ============================================================================
// Components
// ============================================================================

export { LayoutContainer, type LayoutContainerProps } from './components/LayoutContainer';

// Dynamic Plugin Routes (v3.2.0) - for manifest-driven route rendering
// v3.3.0: Added prefetch + lazy cache management APIs (PF1 fix)
export {
  DynamicPluginRoutes,
  renderDynamicRoutes,
  convertToRouteObjects,
  prefetchPluginRoutes,
  clearLazyComponentCache,
  type DynamicPluginRoutesProps,
  type DynamicRoutesOptions,
  type AggregatedRoute,
} from './components/DynamicPluginRoutes';

// HTTP Error Toaster (Stability Reform v1.0 — C-2.3)
export { HttpErrorToaster } from './components/HttpErrorToaster';

// ============================================================================
// Hooks
// ============================================================================

export { useLayout, type UseLayoutResult } from './hooks/useLayout';
export { useResponsive, type UseResponsiveResult } from './hooks/useResponsive';
export { useTheme, type UseThemeResult } from './hooks/useTheme';

// ============================================================================
// Type exports
// ============================================================================

export type {
  // Common types
  ShellBranding,
  NavigationService,
  ShellRoutes,
  // Dashboard Types
  StatCard,
  QuickAction,
  DashboardDataProvider,
  DashboardPageConfig,
  SimpleDashboardConfig,
  // Error Types
  ErrorPageConfig,
  SimpleErrorPageConfig,
  // Placeholder Types (v3.2.0)
  PlaceholderPageProps,
  SimplePlaceholderConfig,
  // Page Assembly Factory Types (v3.2.0 - migrated from Host per R6.4)
  PageNavigationService,
  PageAuthService,
  PageSocialProvider,
  PageFactoryDeps,
  AssembledPages,
} from './pages';

// Layout types
export type { 
  LayoutConfig, 
  LayoutGovernancePolicy, 
  LayoutChangeRequest,
  LayoutState,
  LayoutChangeEvent,
  LayoutRequestResult,
} from './layout-types';

// ============================================================================
// Theme Capability Implementation (merged from platform-theme-web)
// ============================================================================

export { ThemeCapabilityImpl, type ThemeCapabilityConfig } from './theme/ThemeCapabilityImpl';
export { ThemeStore, type ThemeStoreConfig, type ThemeChangeListener } from './theme/ThemeStore';

// Theme presets
export { defaultPreset } from './theme/presets/defaultPreset';
export { compactPreset } from './theme/presets/compactPreset';

// ============================================================================
// Theme configuration (UI style related)
// ============================================================================

export {
  // Types
  type ThemeConfig,
  type FullThemeConfig,
  type BrandingConfig,
  // Default values
  DEFAULT_THEME,
  DEFAULT_FULL_THEME,
  MUI_STYLE_THEME,
  DEFAULT_BRANDING,
  // Utility functions
  mergeTheme,
  mergeBranding,
  createBrandingFromTheme,
} from './theme';

// ============================================================================
// Storage
// ============================================================================

export type { StorageAdapter } from './storage';
export { LocalStorageAdapter, defaultStorage } from './storage';

// ============================================================================
// Router
// ============================================================================

export {
  useCurrentLocation,
  useCurrentPath,
  useShellNavigation,
  type CurrentLocation,
  type ShellNavigationResult,
} from './router';
