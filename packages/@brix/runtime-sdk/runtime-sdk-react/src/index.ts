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
 * @file @brix-sdk/runtime-sdk-react Package Entry
 * @description Runtime SDK React Binding Layer
 * @module @brix-sdk/runtime-sdk-react
 * @version 3.2.0
 *
 * [Architecture Positioning]
 * This package serves as the React binding layer for the Runtime SDK, providing:
 * - React Context encapsulation
 * - React Hooks collection
 *
 * [Dependency Relationship]
 * @brix-sdk/runtime-sdk-api-web (Contract Layer) �� @brix-sdk/runtime-sdk-react (React Binding)
 *
 * [v3.2 Refactoring Notes]
 * Extracted React-related code from @brix-sdk/runtime-sdk-api-web,
 * ensuring the contract layer remains framework-agnostic.
 *
 * @packageDocumentation
 */

// ============================================================================
// Context Exports
// ============================================================================

export {
  RuntimeContextReact,
  RuntimeContextProvider,
} from './context';

// ============================================================================
// Hooks Exports
// ============================================================================

export {
  // Core Hook
  useRuntimeContext,
  
  // Capability Hooks
  useNavigation,
  useAuth,
  useEventBus,
  usePluginState,
  useConfig,
  useHttp,
  useHttpRequest,
  
  // UI Adapter Hooks (v3.2.1 Phase 2)
  useUI,
  useUIOptional,
  
  // Plugin Loader Hook (v3.2.0 D6 Fix)
  usePluginLoader,
  usePluginLoaderOptional,
  
  // Layout and Theme Hooks (migrated from shell-web in v3.1)
  useTheme,
  useLayout,
  useResponsive,
  
  // I18n Hook (v3.3.0 Phase 3.4 i18n chain activation)
  useI18n,
  
  // Tenant Hook (v3.1.0 Phase 1.4 TenantCapability chain)
  useTenant,
  useTenantConfig,

  // ViewMode Hook (Stability Reform v1.0 — C-4)
  useViewMode,

  // Page-state Hooks (Stability Reform v1.0 — C-3)
  usePageState,
  useSubmitGuard,
  useConfirm,
  PAGE_STATE_IDLE,
  PAGE_STATE_LOADING,
  PAGE_STATE_SUCCESS,
  PAGE_STATE_ERROR,
  CONFIRM_DEFAULT_OK_TEXT,
  CONFIRM_DEFAULT_CANCEL_TEXT,

  // Type Exports
  type UseAuthResult,
  type UseEventBusResult,
  type UsePluginStateResult,
  type UseConfigResult,
  type UseHttpResult,
  type HttpRequestState,
  type UseThemeResult,
  type UseLayoutResult,
  type UseResponsiveResult,
  type UseUIResult,
  type UsePluginLoaderResult,
  type UseI18nResult,
  type UseTenantResult,
  type UseTenantConfigResult,
  type UseViewModeResult,

  // Page-state hook types (Stability Reform v1.0 — C-3)
  type PageState,
  type PageStateStatus,
  type UsePageStateResult,
  type UseSubmitGuardResult,
  type ConfirmOptions,
  type UseConfirmResult,
} from './hooks';

// ============================================================================
// Re-export Contract Layer Types (Convenience Imports)
// ============================================================================

export type {
  // Runtime Context
  RuntimeContext,
  
  // Capability Types
  CapabilityType,
  CapabilityRegistry,
  CapabilityProvider,
  
  // Navigation Capability
  NavigationCapability,
  NavigateOptions,
  
  // Authentication Capability
  AuthCapability,
  AuthUser,
  AuthInfo,
  
  // Event Bus Capability
  EventBusCapability,
  EventHandler,
  
  // Plugin State Capability
  PluginStateCapability,
  StateChangeEvent,
  
  // HTTP Capability
  HttpCapability,
  HttpRequestConfig,
  HttpResponse,
  
  // Config Capability
  ConfigStoreCapability,
  ConfigStoreCapabilityType,
  
  // Plugin Related
  PluginManifest,
  PluginContext,
  PluginLifecycle,
  
  // Module Related
  ModuleMetadata,
  ModuleState,
  
  // Plugin Loader Capability (v3.2.0 D6 Fix)
  PluginLoaderCapability,
  PluginLoaderCapabilityType,
  RemoteComponentProps,
  
  // I18n Capability (v3.3.0 Phase 3.4)
  I18nCapability,
  I18nCapabilityType,
  LanguageBundle,
  TranslateOptions,
  LocaleCode,
} from '@brix-sdk/runtime-sdk-api-web';
