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
 * @file ViewMode Capability Type Definitions
 * @description Defines the {@link ViewModeCapability} contract — explicit modeling
 * of the platform-administrator view perspective per the B2B2C tenancy model.
 * Mirror of the Java `io.runtime.sdk.capability.ViewModeCapability` interface.
 * @module @brix-sdk/runtime-sdk-api-web/types/view-mode
 * @version 3.3.0
 *
 * [Phase 2 — C-4 ViewMode]
 * Adds the missing &ldquo;view perspective&rdquo; concept that previously lived
 * implicitly inside `TenantCapability.switchTenant`. A platform admin may now
 * explicitly switch to `TENANT_ACTOR` / `TENANT_SUBJECT` view, with the JWT
 * being re-issued by the backend and the front-end performing a full reload
 * to guarantee `RuntimeContext` isolation.
 *
 * [Architecture Layer]
 * Layer 2A: Capability Contract — pure interface definition, no implementation.
 * Implementation lives in `platform-tenant-web` (Layer 2C).
 *
 * [Red Lines — see plan §4.5]
 * - Plugins MUST NOT decode JWT payload directly to determine view mode.
 * - Dual-token (`platformAdminToken` + `tenantToken`) pattern is forbidden.
 * - View-mode switch MUST trigger a full page reload (`window.location.reload()`),
 *   not an SPA route push, to avoid leaking the previous `RuntimeContext`.
 */

// =========================================
// View Mode Capability Type Identifier
// =========================================

/**
 * View Mode Capability Type Identifier.
 *
 * Used for capability registration and lookup in the Runtime Context.
 * The `Symbol.for()` form ensures cross-realm equality (e.g. when
 * `runtime-sdk-api-web` is shared via Module Federation).
 *
 * @example
 * ```typescript
 * // Registration (Host layer)
 * runtime.registerCapability(ViewModeCapabilityType, { provide: () => viewModeCapability });
 *
 * // Lookup (Plugin layer — preferred via useViewMode())
 * const vm = context.getCapability<ViewModeCapability>(ViewModeCapabilityType);
 * ```
 */
export const ViewModeCapabilityType = Symbol.for('ViewModeCapability');

// =========================================
// View Mode Enum
// =========================================

/**
 * Platform-admin view-mode literal values.
 * Mirror of the Java `ViewModeCapability.ViewMode` enum.
 */
export const VIEW_MODE_PLATFORM_ADMIN = 'PLATFORM_ADMIN' as const;
export const VIEW_MODE_TENANT_ACTOR = 'TENANT_ACTOR' as const;
export const VIEW_MODE_TENANT_SUBJECT = 'TENANT_SUBJECT' as const;

/**
 * The three explicit view modes per the B2B2C tenancy model.
 *
 * - `PLATFORM_ADMIN`  — operating as platform super-admin / ops (no tenant context).
 * - `TENANT_ACTOR`    — platform admin viewing a tenant from the B-side perspective.
 * - `TENANT_SUBJECT`  — platform admin viewing a tenant from the C-side perspective
 *                       (forward-compatible; currently shares wire format with
 *                       `TENANT_ACTOR` per plan §4.3).
 *
 * The viewing semantics are expressed by the JWT carrying both `tenant_id`
 * and `original_sub` claims; `role` itself remains `platform-admin`.
 */
export type ViewMode =
  | typeof VIEW_MODE_PLATFORM_ADMIN
  | typeof VIEW_MODE_TENANT_ACTOR
  | typeof VIEW_MODE_TENANT_SUBJECT;

// =========================================
// Switch Request / Result
// =========================================

/**
 * Request payload for {@link ViewModeCapability.switchTo}.
 *
 * Cross-field invariants (enforced by the implementation):
 * - `tenantId` is required when `mode !== 'PLATFORM_ADMIN'`.
 */
export interface ViewModeSwitchRequest {
  /** Target view mode. */
  readonly mode: ViewMode;
  /** Target tenant ID (required when {@link mode} is not `PLATFORM_ADMIN`). */
  readonly tenantId?: string;
}

/**
 * Result returned by {@link ViewModeCapability.switchTo}.
 *
 * The implementation is expected to persist {@link accessToken} via the
 * authentication storage layer and then trigger a full page reload before
 * resolving — callers therefore typically do not need to consume this object
 * directly. It is exposed primarily for testability.
 */
export interface ViewModeSwitchResult {
  /** Freshly signed JWT carrying the new view perspective. */
  readonly accessToken: string;
  /** Access-token lifetime, in seconds. */
  readonly expiresInSeconds: number;
  /** The view mode now in effect. */
  readonly mode: ViewMode;
  /** The tenant currently being viewed (omitted for `PLATFORM_ADMIN`). */
  readonly tenantId?: string;
  /**
   * The original platform-admin identity that initiated the impersonation;
   * always populated when {@link mode} is not `PLATFORM_ADMIN`.
   */
  readonly originalSub?: string;
}

// =========================================
// Listener
// =========================================

/**
 * Listener invoked when the view mode changes.
 *
 * Note: in practice the front-end performs a full reload after a successful
 * switch, so listeners primarily fire on the *initial* hydration of the
 * capability after reload (if the implementation chooses to emit on bootstrap).
 */
export type ViewModeChangeListener = (event: ViewModeChangeEvent) => void;

/**
 * Event payload delivered to {@link ViewModeChangeListener}.
 */
export interface ViewModeChangeEvent {
  readonly mode: ViewMode;
  readonly tenantId?: string;
  readonly originalSub?: string;
}

// =========================================
// Capability Contract
// =========================================

/**
 * View Mode Capability Contract (Layer 2A).
 *
 * Provides the explicit perspective concept needed to:
 * - Render a banner when a platform admin is impersonating a tenant view.
 * - Filter menu items via plugin-manifest `requiredViewMode`.
 * - Guard routes via `RequireViewMode` so direct URL hits are blocked.
 */
export interface ViewModeCapability {
  /** Returns the current view mode resolved from the active session. */
  getCurrent(): ViewMode;

  /**
   * Returns the original platform-admin identity if the current session
   * represents an impersonation, otherwise `null`.
   */
  getOriginalSub(): string | null;

  /**
   * Returns the tenant ID currently being viewed, or `null` for
   * `PLATFORM_ADMIN`.
   */
  getViewingTenantId(): string | null;

  /**
   * Switches to the requested view mode. The implementation MUST:
   * 1. Call the backend `POST /api/auth/view-mode/switch` endpoint.
   * 2. Persist the returned access token via the auth-storage layer.
   * 3. Trigger a full page reload (`window.location.reload()`).
   *
   * The returned promise typically does not resolve in normal flow because
   * the page reload occurs first. It is exposed as a Promise for testability.
   */
  switchTo(request: ViewModeSwitchRequest): Promise<ViewModeSwitchResult>;

  /** Subscribes to view-mode changes; returns an unsubscribe function. */
  onViewModeChange(listener: ViewModeChangeListener): () => void;
}

// =========================================
// Capability Config (Host injection)
// =========================================

/**
 * Configuration object the Host layer passes when constructing the
 * {@link ViewModeCapability} implementation. Mirrors the
 * `TenantCapabilityConfig` pattern.
 */
export interface ViewModeCapabilityConfig {
  /**
   * Resolves the current view mode from the active session/JWT.
   * Implementations should derive this from the auth-storage layer rather
   * than decoding the JWT payload in plugin code.
   */
  readonly getCurrent: () => ViewMode;
  /** Resolves the original platform-admin identity, or `null`. */
  readonly getOriginalSub: () => string | null;
  /** Resolves the tenant currently being viewed, or `null`. */
  readonly getViewingTenantId: () => string | null;
  /**
   * Performs the backend switch + token persistence + reload.
   * Returns the parsed switch result for testability.
   */
  readonly switchTo: (
    request: ViewModeSwitchRequest
  ) => Promise<ViewModeSwitchResult>;
}
