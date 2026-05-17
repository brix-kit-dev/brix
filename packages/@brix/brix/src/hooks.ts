/**
 * @brix-sdk/brix/hooks - React hooks for Brix runtime
 * 
 * @example
 * ```typescript
 * import { 
 *   useAuth,
 *   useHttp,
 *   useNavigation,
 *   useConfig 
 * } from '@brix-sdk/brix/hooks';
 * ```
 */

// ============================================================
// All React hooks from runtime-sdk-react
// ============================================================

export {
  // Runtime Context
  useRuntimeContext,
  
  // Authentication
  useAuth,
  
  // Configuration
  useConfig,
  
  // Event Bus
  useEventBus,
  
  // HTTP Client
  useHttp,
  useHttpRequest,
  
  // Layout
  useLayout,
  
  // Navigation
  useNavigation,
  
  // Plugin System
  usePluginLoader,
  usePluginLoaderOptional,
  usePluginState,
  
  // Responsive
  useResponsive,
  
  // Theme
  useTheme,
  
  // UI
  useUI,
  useUIOptional,
  
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
  type UsePluginLoaderResult
} from '@brix-sdk/runtime-sdk-react';
