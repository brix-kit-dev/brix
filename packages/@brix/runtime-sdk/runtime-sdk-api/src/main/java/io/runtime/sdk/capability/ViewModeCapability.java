/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * View Mode Capability Contract — explicit modeling of platform-administrator
 * view perspective (Layer 2A).
 *
 * <p>Per <i>v3.0.9 Runtime Shell Blueprint</i> and the B2B2C tenancy model,
 * a {@code PLATFORM_ADMIN} user may temporarily impersonate a tenant view
 * for support / debugging purposes. The view-mode switch is a privileged
 * operation that must:</p>
 * <ol>
 *   <li>Verify the caller currently holds the {@code platform-admin} role
 *       via {@link AuthContextCapability}.</li>
 *   <li>Re-issue a brand-new JWT via {@link JwtIssuerCapability} preserving
 *       {@code role=platform-admin} but carrying a {@code tenant_id} (the
 *       tenant being viewed) and an {@code original_sub} claim recording
 *       the platform-admin identity that initiated the view session. Per
 *       <i>plan §4.3</i> {@code mid}/{@code pid} are deliberately omitted —
 *       this is a tenant-context binding, not impersonation of a specific
 *       member or principal.</li>
 *   <li>Append an audit record so every switch is traceable
 *       (who, when, target tenant, target view-mode).</li>
 * </ol>
 *
 * <h3>Red Lines</h3>
 * <ul>
 *   <li>Frontend MUST NOT decode or trust JWT payload directly to determine
 *       view mode — always go through this capability.</li>
 *   <li>The dual-token pattern (front-end holding both
 *       {@code platformAdminToken} and {@code tenantToken}) is forbidden.</li>
 *   <li>After {@link #switchTo} succeeds the front-end MUST perform a full
 *       page reload — SPA-internal route push leaks the previous
 *       {@code RuntimeContext}.</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 * @see AuthContextCapability
 * @see JwtIssuerCapability
 * @see TenantCapability
 */
@Since("3.3.0")
public interface ViewModeCapability {

    /**
     * Returns the current view mode resolved from the active JWT.
     *
     * <p>Resolution rules:</p>
     * <ul>
     *   <li>Token with {@code role=platform-admin} and no {@code original_sub}
     *       → {@link ViewMode#PLATFORM_ADMIN}</li>
     *   <li>Token with {@code role=platform-admin}, {@code tenant_id} populated
     *       and {@code original_sub} populated → {@link ViewMode#TENANT_ACTOR}
     *       (the platform admin is viewing the tenant; viewing semantics are
     *       expressed via {@code original_sub} + {@code tenant_id}, not by
     *       changing the role).</li>
     *   <li>Reserved: {@link ViewMode#TENANT_SUBJECT} is modelled for forward
     *       compatibility (C-side perspective) but currently maps to the same
     *       wire representation as {@link ViewMode#TENANT_ACTOR}.</li>
     * </ul>
     *
     * @return the current view mode, never {@code null}
     * @throws ViewModeResolutionException if no authenticated context is present
     */
    ViewMode getCurrent();

    /**
     * Returns the {@code originalSub} claim if the current token represents a
     * platform-admin impersonation session, otherwise empty.
     *
     * <p>Used to render the &ldquo;exit super-admin view&rdquo; banner.</p>
     *
     * @return the original platform-admin identity ID if impersonating
     */
    Optional<String> getOriginalSub();

    /**
     * Switches the current session to the requested view mode and returns a
     * freshly signed JWT carrying the new perspective.
     *
     * <p>The implementation MUST:</p>
     * <ol>
     *   <li>Verify the caller currently holds {@code platform-admin} (or a
     *       previously-impersonating session originating from a platform-admin).</li>
     *   <li>Validate {@code request.tenantId()} is non-null when
     *       {@code request.mode()} is {@link ViewMode#TENANT_ACTOR} or
     *       {@link ViewMode#TENANT_SUBJECT}.</li>
     *   <li>Sign a new JWT via {@link JwtIssuerCapability} preserving the
     *       {@code originalSub} claim (or setting it on first impersonation).</li>
     *   <li>Append an audit-log entry capturing the switch.</li>
     * </ol>
     *
     * @param request the switch request (mode + optional tenant)
     * @return the freshly signed JWT to be returned to the front-end
     * @throws ViewModeSwitchDeniedException if the caller is not authorized
     * @throws IllegalArgumentException if {@code request} is invalid
     */
    SwitchResult switchTo(SwitchRequest request);

    // ========== Value Types ==========

    /**
     * The three explicit view modes per the B2B2C tenancy model.
     *
     * <ul>
     *   <li>{@link #PLATFORM_ADMIN} — operating as platform super-admin / ops
     *       (no tenant context).</li>
     *   <li>{@link #TENANT_ACTOR} — operating <i>as a B-side member</i>
     *       (carries {@code mid}; can perform tenant-management operations).</li>
     *   <li>{@link #TENANT_SUBJECT} — operating <i>as a C-side end-user</i>
     *       (carries {@code pid}; read-only / consumer experience).</li>
     * </ul>
     */
    enum ViewMode {
        PLATFORM_ADMIN,
        TENANT_ACTOR,
        TENANT_SUBJECT
    }

    /**
     * Request to switch view mode.
     *
     * @param mode      target view mode (required)
     * @param tenantId  target tenant ID; required when {@code mode != PLATFORM_ADMIN}
     */
    record SwitchRequest(
            ViewMode mode,
            Long tenantId
    ) {
        /**
         * Compact constructor enforcing the cross-field invariants.
         */
        public SwitchRequest {
            if (mode == null) {
                throw new IllegalArgumentException("ViewMode must not be null");
            }
            if (mode != ViewMode.PLATFORM_ADMIN && tenantId == null) {
                throw new IllegalArgumentException(
                        "tenantId is required for view mode " + mode);
            }
        }
    }

    /**
     * Result of a successful view-mode switch.
     *
     * @param accessToken          the freshly signed access JWT (carries {@code originalSub})
     * @param expiresInSeconds     access-token lifetime, in seconds
     * @param mode                 the view mode now in effect
     * @param tenantId             the tenant currently being viewed (may be {@code null}
     *                             when {@code mode == PLATFORM_ADMIN})
     * @param originalSub          the platform-admin identity that initiated the
     *                             impersonation (always populated for
     *                             non-{@code PLATFORM_ADMIN} switches)
     */
    record SwitchResult(
            String accessToken,
            long expiresInSeconds,
            ViewMode mode,
            Long tenantId,
            String originalSub
    ) {}
}
