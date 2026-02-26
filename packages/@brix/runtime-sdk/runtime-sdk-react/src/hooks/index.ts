/**
 * @file Hooks Module Entry
 * @description Export all React Hooks
 * @module @brix/runtime-sdk-react/hooks
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 * Added useTheme, useLayout, useResponsive hooks migrated from shell-web.
 *
 * [v3.2.1 UI Adapter Support]
 * Added useUI hook for accessing UIAdapter from RuntimeContext.
 */

export { useRuntimeContext } from './useRuntimeContext';
export { useNavigation } from './useNavigation';
export { useAuth, type UseAuthResult } from './useAuth';
export { useEventBus, type UseEventBusResult } from './useEventBus';
export { usePluginState, type UsePluginStateResult } from './usePluginState';
export { useConfig, type UseConfigResult } from './useConfig';
export { 
  useHttp, 
  useHttpRequest,
  type UseHttpResult,
  type HttpRequestState 
} from './useHttp';

// Layout and Theme Hooks (migrated from shell-web in v3.1)
export { useTheme, type UseThemeResult } from './useTheme';
export { useLayout, type UseLayoutResult } from './useLayout';
export { useResponsive, type UseResponsiveResult } from './useResponsive';

// UI Adapter Hook (v3.2.1 Phase 2 UI Adapter)
export { useUI, useUIOptional, type UseUIResult } from './useUI';

// Plugin Loader Hook (v3.2.0 D6 Fix)
export { usePluginLoader, usePluginLoaderOptional, type UsePluginLoaderResult } from './usePluginLoader';
