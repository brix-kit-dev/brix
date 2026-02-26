/**
 * @file @brix/runtime-sdk-react Package Entry
 * @description Runtime SDK React Binding Layer
 * @module @brix/runtime-sdk-react
 * @version 3.2.0
 *
 * [Architecture Positioning]
 * This package serves as the React binding layer for the Runtime SDK, providing:
 * - React Context encapsulation
 * - React Hooks collection
 *
 * [Dependency Relationship]
 * @brix/runtime-sdk-api-web (Contract Layer) ← @brix/runtime-sdk-react (React Binding)
 *
 * [v3.2 Refactoring Notes]
 * Extracted React-related code from @brix/runtime-sdk-api-web,
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
  ConfigCapability,
  
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
} from '@brix/runtime-sdk-api-web';
