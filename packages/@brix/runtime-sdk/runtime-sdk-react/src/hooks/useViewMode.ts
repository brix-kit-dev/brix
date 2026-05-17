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
 * @file useViewMode Hook — Phase 2 / C-4 ViewModeCapability React Hook
 * @description Resolves {@link ViewModeCapability} from `RuntimeContext` and
 * exposes a reactive view-mode state to React components, mirroring
 * {@link useTenant}.
 *
 * @module @brix-sdk/runtime-sdk-react/hooks/useViewMode
 * @version 3.3.0
 *
 * [Architecture Layer]
 * React-binding layer — bridges the {@link ViewModeCapability} contract to
 * React components. The Host registers `ViewModeCapabilityImpl` during
 * bootstrap; plugins consume this hook without knowing the implementation.
 *
 * @since 3.3.0
 * @see ViewModeCapability — Contract in `runtime-sdk-api-web`
 * @see ViewModeCapabilityImpl — Implementation in `platform-tenant-web`
 */

import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  type ViewMode,
  type ViewModeCapability,
  type ViewModeSwitchRequest,
  type ViewModeSwitchResult,
  ViewModeCapabilityType,
} from '@brix-sdk/runtime-sdk-api-web';

import { useRuntimeContext } from './useRuntimeContext';

/**
 * Return shape of {@link useViewMode}.
 */
export interface UseViewModeResult {
  /** Current view mode resolved from the active session. */
  readonly mode: ViewMode;
  /**
   * Original platform-admin identity that initiated the viewing session,
   * or `null` for {@code PLATFORM_ADMIN}.
   */
  readonly originalSub: string | null;
  /** Tenant currently being viewed, or `null` for {@code PLATFORM_ADMIN}. */
  readonly viewingTenantId: string | null;
  /** Convenience predicate — `true` iff {@link originalSub} is non-null. */
  readonly isViewingAsTenant: boolean;
  /**
   * Switches the view mode. Triggers a full page reload after the backend
   * round-trip — the returned promise typically does not resolve in normal
   * flow.
   */
  readonly switchTo: (
    request: ViewModeSwitchRequest,
  ) => Promise<ViewModeSwitchResult>;
  /** The raw {@link ViewModeCapability} for advanced usage. */
  readonly capability: ViewModeCapability;
}

/**
 * Phase 2 / C-4 — View Mode hook.
 *
 * @example
 * ```tsx
 * function PlatformAdminBanner() {
 *   const { isViewingAsTenant, viewingTenantId, switchTo } = useViewMode();
 *   if (!isViewingAsTenant) return null;
 *   return (
 *     <div role="alert">
 *       Viewing tenant {viewingTenantId} as platform admin —
 *       <button onClick={() => switchTo({ mode: 'PLATFORM_ADMIN' })}>
 *         exit
 *       </button>
 *     </div>
 *   );
 * }
 * ```
 *
 * @throws Error if used outside `RuntimeContextProvider`.
 * @throws Error if `ViewModeCapability` is not registered.
 */
export function useViewMode(): UseViewModeResult {
  const context = useRuntimeContext();

  const viewModeCapability = useMemo(() => {
    const capability = context.getCapability<ViewModeCapability>(
      ViewModeCapabilityType,
    );
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] ViewModeCapability is not registered in RuntimeContext. ' +
          'Ensure the Host registers ViewModeCapability in bootstrap via ' +
          'runtime.registerCapability(ViewModeCapabilityType, viewModeCapability).',
      );
    }
    return capability;
  }, [context]);

  const [mode, setMode] = useState<ViewMode>(() => viewModeCapability.getCurrent());
  const [originalSub, setOriginalSub] = useState<string | null>(() =>
    viewModeCapability.getOriginalSub(),
  );
  const [viewingTenantId, setViewingTenantId] = useState<string | null>(() =>
    viewModeCapability.getViewingTenantId(),
  );

  useEffect(() => {
    const unsubscribe = viewModeCapability.onViewModeChange((event) => {
      setMode(event.mode);
      setOriginalSub(event.originalSub ?? null);
      setViewingTenantId(event.tenantId ?? null);
    });
    return unsubscribe;
  }, [viewModeCapability]);

  const switchTo = useCallback(
    (request: ViewModeSwitchRequest) => viewModeCapability.switchTo(request),
    [viewModeCapability],
  );

  const isViewingAsTenant = originalSub !== null;

  return {
    mode,
    originalSub,
    viewingTenantId,
    isViewingAsTenant,
    switchTo,
    capability: viewModeCapability,
  };
}
