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
 * @file ViewModeCapabilityImpl — Phase 2 / C-4 ViewModeCapability Implementation
 * @description Bridges the Host-provided view-mode state and switch RPC to the
 * formal {@link ViewModeCapability} contract from `runtime-sdk-api-web`.
 * Mirrors the {@link TenantCapabilityImpl} config-delegation pattern.
 *
 * @module @brix-sdk/platform-tenant-web/ViewModeCapabilityImpl
 * @version 3.3.0
 *
 * [Architecture Layer]
 * Layer 2C: Capability Implementation. The Host is responsible for sourcing
 * `getCurrent` / `getOriginalSub` / `getViewingTenantId` from the JWT-aware
 * auth-storage layer and for performing the actual REST switch (so that token
 * persistence and full page reload happen in one centralised place).
 *
 * [Red Lines — see plan §4.5]
 * - Plugins MUST NOT decode JWT directly; always go through this capability.
 * - Switch implementations MUST trigger `window.location.reload()` after
 *   persisting the new token to guarantee `RuntimeContext` isolation.
 *
 * @since 3.3.0
 */

import type {
  ViewMode,
  ViewModeCapability,
  ViewModeCapabilityConfig,
  ViewModeChangeEvent,
  ViewModeChangeListener,
  ViewModeSwitchRequest,
  ViewModeSwitchResult,
} from '@brix-sdk/runtime-sdk-api-web';

// Re-export contract-layer type for ergonomic Host wiring.
export type { ViewModeCapabilityConfig };

/**
 * ViewModeCapability Implementation — config-delegation pattern.
 *
 * Single-threaded by design (browser main thread). Listener notifications
 * are best-effort: in normal flow the page reloads after a successful switch
 * before any listener would fire, so subscribers are mainly used for the
 * initial hydration broadcast (if the Host opts in to emit one).
 */
export class ViewModeCapabilityImpl implements ViewModeCapability {
  private readonly config: ViewModeCapabilityConfig;
  private readonly listeners: Set<ViewModeChangeListener> = new Set();
  private destroyed = false;

  /**
   * Creates a new ViewModeCapabilityImpl instance.
   *
   * @param config - Host-provided callbacks for view-mode state + RPC.
   * @throws Error if {@link config} is null or undefined.
   */
  constructor(config: ViewModeCapabilityConfig) {
    if (!config) {
      throw new Error(
        '[ViewModeCapabilityImpl] Configuration is required. ' +
          'Provide view-mode accessors via ViewModeCapabilityConfig.',
      );
    }
    this.config = config;
  }

  /**
   * Returns the current view mode resolved from the active session/JWT.
   * Delegates entirely to the Host-provided accessor.
   */
  getCurrent(): ViewMode {
    return this.config.getCurrent();
  }

  /**
   * Returns the original platform-admin identity if the current session
   * represents a viewing session, otherwise `null`.
   */
  getOriginalSub(): string | null {
    return this.config.getOriginalSub();
  }

  /**
   * Returns the tenant ID currently being viewed, or `null` when the active
   * mode is `PLATFORM_ADMIN`.
   */
  getViewingTenantId(): string | null {
    return this.config.getViewingTenantId();
  }

  /**
   * Switches the current session to {@link request.mode}. Implementations
   * provided by the Host MUST persist the returned access token via the
   * auth-storage layer and trigger a full page reload before resolving.
   *
   * @param request - target view mode + optional tenant.
   * @returns the parsed switch result (primarily for testability).
   */
  async switchTo(request: ViewModeSwitchRequest): Promise<ViewModeSwitchResult> {
    if (!request) {
      throw new Error('[ViewModeCapabilityImpl] SwitchRequest is required.');
    }
    if (!request.mode) {
      throw new Error('[ViewModeCapabilityImpl] SwitchRequest.mode is required.');
    }
    if (request.mode !== 'PLATFORM_ADMIN' && !request.tenantId) {
      throw new Error(
        '[ViewModeCapabilityImpl] tenantId is required when mode is not PLATFORM_ADMIN.',
      );
    }

    const result = await this.config.switchTo(request);

    // Best-effort listener notification — typically pre-empted by reload.
    const event: ViewModeChangeEvent = {
      mode: result.mode,
      tenantId: result.tenantId,
      originalSub: result.originalSub,
    };
    this.notifyListeners(event);

    return result;
  }

  /**
   * Subscribes to view-mode change events; returns the unsubscribe function.
   * No-op after {@link destroy} has been called.
   */
  onViewModeChange(listener: ViewModeChangeListener): () => void {
    if (this.destroyed) {
      return () => {
        /* no-op — capability already destroyed */
      };
    }
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Releases listener references. Idempotent.
   */
  destroy(): void {
    this.destroyed = true;
    this.listeners.clear();
  }

  private notifyListeners(event: ViewModeChangeEvent): void {
    this.listeners.forEach((listener) => {
      try {
        listener(event);
      } catch (error) {
        // Listener errors must not break the switch flow.
        // Use console.error rather than a logger to avoid pulling extra deps.
        // eslint-disable-next-line no-console
        console.error('[ViewModeCapabilityImpl] listener threw:', error);
      }
    });
  }
}
