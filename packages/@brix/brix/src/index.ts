/**
 * @brix-sdk/brix - Brix Platform SDK
 * 
 * One package for all Brix capabilities (Firebase v9 style)
 * 
 * @example Basic usage
 * ```bash
 * npm install @brix-sdk/brix
 * ```
 * 
 * @example Import hooks (most common)
 * ```typescript
 * import { useAuth, useHttp, useNavigation, useConfig } from '@brix-sdk/brix';
 * ```
 * 
 * @example Import from runtime modules
 * ```typescript
 * import { RuntimeContext } from '@brix-sdk/brix/runtime';
 * ```
 */

// ============================================================
// Most commonly used exports (hooks)
// ============================================================

export {
  useAuth,
  useConfig,
  useEventBus,
  useHttp,
  useHttpRequest,
  useLayout,
  useNavigation,
  usePluginLoader,
  usePluginLoaderOptional,
  usePluginState,
  useResponsive,
  useRuntimeContext,
  useTheme,
  useUI,
  useUIOptional
} from '@brix-sdk/runtime-sdk-react';

// ============================================================
// Core types and contracts
// ============================================================

export type {
  RuntimeContext,
  PluginDefinition,
  CapabilityType,
  CapabilityRegistry,
  AuthCapability,
  ConfigStoreCapability,
  HttpCapability,
  NavigationCapability,
  EventBusCapability,
  PluginStateCapability,
  ThemeCapability,
  I18nCapability,
  LayoutCapability,
  UIAdapter
} from '@brix-sdk/runtime-sdk-api-web';

export { UICapabilityType } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================
// Version info
// ============================================================

export const VERSION = '1.0.0';
