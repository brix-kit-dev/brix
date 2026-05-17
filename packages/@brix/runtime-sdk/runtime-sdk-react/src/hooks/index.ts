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
 * @file Hooks Module Entry
 * @description Export all React Hooks
 * @module @brix-sdk/runtime-sdk-react/hooks
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix-sdk/runtime-sdk-api-web to a standalone React binding package.
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

// I18n Hook (v3.3.0 Phase 3.4 i18n chain activation)
export { useI18n, type UseI18nResult } from './useI18n';

// Tenant Hook (v3.1.0 Phase 1.4 TenantCapability chain)
export { useTenant, type UseTenantResult } from './useTenant';
export { useViewMode, type UseViewModeResult } from './useViewMode';

// Tenant Config Hook (v3.1.0 Phase 3 Three-layer merge)
export { useTenantConfig, type UseTenantConfigResult } from './useTenantConfig';

// =====================================================================
// Stability Reform v1.0 — C-3 Page-state hooks (eliminate boilerplate)
// =====================================================================
export {
  usePageState,
  PAGE_STATE_IDLE,
  PAGE_STATE_LOADING,
  PAGE_STATE_SUCCESS,
  PAGE_STATE_ERROR,
  type PageState,
  type PageStateStatus,
  type UsePageStateResult,
  type PageStateRenderOverrides,
  type IsEmptyPredicate,
} from './usePageState';
export { useSubmitGuard, type UseSubmitGuardResult } from './useSubmitGuard';
export {
  useConfirm,
  CONFIRM_DEFAULT_OK_TEXT,
  CONFIRM_DEFAULT_CANCEL_TEXT,
  type ConfirmOptions,
  type UseConfirmResult,
} from './useConfirm';

// =====================================================================
// Stability Reform v1.0 — C-8 Form-state convergence
// `useForm` is also exposed via `useUI().Form.useForm` (compound component
// pattern); both entry points share this single implementation.
// =====================================================================
export { useForm } from './useForm';
