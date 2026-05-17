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
 * @file HttpErrorToaster
 * @description Mount-once subscriber that turns `system.http.error` events
 *              into toasts via the active UI adapter.
 * @module @brix-sdk/platform-frame-web/components/HttpErrorToaster
 * @version 3.3.0
 *
 * [Frontend Stability Reform Plan v1.0 — C-2.3]
 * - Single-source-of-truth for HTTP failure presentation
 * - Lives in the Shell layer (Layer 2B) — depends on `useUI` + `useEventBus`
 *   only; no direct MUI / DOM imports
 * - Skips `auth` errors so the existing 401 re-login flow remains the
 *   exclusive owner of session expiry UX
 * - Dedups identical messages within `HTTP_ERROR_TOAST_DEDUP_WINDOW_MS`
 */

import { useEffect, useRef } from 'react';
import {
  HTTP_ERROR_TOAST_DEDUP_WINDOW_MS,
  SYSTEM_HTTP_ERROR_EVENT,
  type SystemHttpErrorKind,
  type SystemHttpErrorPayload,
} from '@brix-sdk/runtime-sdk-api-web';
import { useEventBus, useUI } from '@brix-sdk/runtime-sdk-react';

// ============================================================================
// Toast severity routing — extracted as named constants per Redline §22
// ============================================================================

const TOAST_DURATION_MS_DEFAULT = 4000 as const;
const TOAST_DURATION_MS_SERVER = 6000 as const;
const TOAST_DURATION_MS_NETWORK = 5000 as const;

/**
 * Map a {@link SystemHttpErrorKind} to a toast duration in milliseconds.
 */
function resolveToastDurationMs(kind: SystemHttpErrorKind): number {
  switch (kind) {
    case 'server':
      return TOAST_DURATION_MS_SERVER;
    case 'network':
    case 'timeout':
      return TOAST_DURATION_MS_NETWORK;
    default:
      return TOAST_DURATION_MS_DEFAULT;
  }
}

/**
 * Build a stable dedup key from the payload. Two errors with identical kind +
 * sanitized URL + status + message collapse into a single toast within the
 * dedup window.
 */
function buildDedupKey(payload: SystemHttpErrorPayload): string {
  return `${payload.kind}::${payload.method}::${payload.url}::${payload.status}::${payload.message}`;
}

// ============================================================================
// Component
// ============================================================================

/**
 * HttpErrorToaster
 *
 * Mount once near the application root (inside the RuntimeContext provider
 * tree). Subscribes to `SYSTEM_HTTP_ERROR_EVENT` and routes messages through
 * the UI adapter's `MessageAPI`.
 *
 * Renders nothing — purely a side-effect host.
 *
 * @example
 * ```tsx
 * <RuntimeContextProvider value={runtime}>
 *   <ThemeProvider theme="light">
 *     <HttpErrorToaster />
 *     <App />
 *   </ThemeProvider>
 * </RuntimeContextProvider>
 * ```
 */
export function HttpErrorToaster(): null {
  const { on } = useEventBus();
  const { message } = useUI();

  // Refs survive HMR-induced re-mounts cleanly and do not trigger re-renders.
  const messageRef = useRef(message);
  messageRef.current = message;

  const dedupCacheRef = useRef<Map<string, number>>(new Map());

  useEffect(() => {
    const handler = (payload: SystemHttpErrorPayload | undefined): void => {
      if (payload === undefined) {
        return;
      }

      // Skip 401 — auth-expired flow drives its own UX (re-login redirect).
      if (payload.kind === 'auth') {
        return;
      }

      const dedupKey = buildDedupKey(payload);
      const now = Date.now();
      const lastShownAt = dedupCacheRef.current.get(dedupKey);
      if (lastShownAt !== undefined && now - lastShownAt < HTTP_ERROR_TOAST_DEDUP_WINDOW_MS) {
        return;
      }
      dedupCacheRef.current.set(dedupKey, now);

      // Opportunistic eviction: prevents unbounded growth under abuse.
      if (dedupCacheRef.current.size > 1000) {
        const cutoff = now - HTTP_ERROR_TOAST_DEDUP_WINDOW_MS;
        for (const [key, ts] of dedupCacheRef.current) {
          if (ts < cutoff) {
            dedupCacheRef.current.delete(key);
          }
        }
      }

      const duration = resolveToastDurationMs(payload.kind);
      const content = payload.message;

      switch (payload.kind) {
        case 'forbidden':
        case 'client':
          messageRef.current.warning({ content, duration });
          return;
        case 'network':
        case 'timeout':
        case 'server':
        default:
          messageRef.current.error({ content, duration });
          return;
      }
    };

    on<SystemHttpErrorPayload>(SYSTEM_HTTP_ERROR_EVENT, handler);
    // useEventBus() automatically removes subscriptions on unmount, so no
    // explicit `off` is required here.
  }, [on]);

  return null;
}

export default HttpErrorToaster;
