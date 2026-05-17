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
 * @file RequireViewMode — Phase 2 / C-4 route guard
 * @description Renders {@code children} only when the current
 * {@link ViewMode} matches one of the {@code allowed} values. Otherwise
 * either renders the supplied {@code fallback} or, by default, returns
 * `null` so the surrounding router can show its 404/403 page.
 *
 * @module @brix-sdk/platform-tenant-web/components/RequireViewMode
 * @version 3.3.0
 *
 * [Architecture Compliance]
 * Layer 3 (Presentation). Resolves view mode through the
 * {@link useViewMode} hook — does NOT couple to a specific router (R-3).
 *
 * @since 3.3.0
 */

import React from 'react';
import { useViewMode } from '@brix-sdk/runtime-sdk-react';
import type { ViewMode } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Public props for {@link RequireViewMode}.
 */
export interface RequireViewModeProps {
  /**
   * The view mode (or set of modes) that may render {@code children}. A
   * scalar value is treated as a single-element set.
   */
  readonly allowed: ViewMode | readonly ViewMode[];
  /**
   * Optional fallback element rendered when the current view mode is not
   * in {@link allowed}. Defaults to `null` — Hosts typically supply a
   * dedicated 403/redirect element.
   */
  readonly fallback?: React.ReactNode;
  /** Protected children. */
  readonly children: React.ReactNode;
}

/**
 * Conditionally renders its {@code children} based on the active view
 * mode. Does NOT navigate on its own — that responsibility belongs to the
 * caller (e.g. wrap with `<Navigate />` inside the supplied fallback) so
 * the guard remains router-agnostic.
 */
export const RequireViewMode: React.FC<RequireViewModeProps> = ({
  allowed,
  fallback = null,
  children,
}) => {
  const { mode } = useViewMode();
  const allowedSet: readonly ViewMode[] = Array.isArray(allowed)
    ? (allowed as readonly ViewMode[])
    : [allowed as ViewMode];
  if (!allowedSet.includes(mode)) {
    return <>{fallback}</>;
  }
  return <>{children}</>;
};
