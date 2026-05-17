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
package io.runtime.sdk.event.tenant;

import java.time.Instant;

import io.runtime.sdk.event.IntegrationEvent;
import io.runtime.sdk.event.SchemaVersion;

/**
 * Integration event published when a principal (Subject) leaves a tenant.
 *
 * <p>This event signals that a C-side (Subject) relationship has been
 * terminated between an identity and a tenant. Industry plugins should
 * listen to this event to handle domain-specific cleanup or state changes.
 *
 * <h3>Trigger Conditions</h3>
 * <p>A PrincipalLeftEvent is published when:
 * <ul>
 *   <li>Subject voluntarily exits the tenant (unbinds)</li>
 *   <li>Tenant admin revokes the principal</li>
 *   <li>Principal status set to REVOKED</li>
 * </ul>
 *
 * <h3>Important: Business Object Lifecycle</h3>
 * <p>Business object completion (Case closure, Order fulfillment) does NOT
 * trigger this event. Principal lifecycle is independent of business objects.
 *
 * <h3>Subscriber Example</h3>
 * <pre>{@code
 * @EventListener
 * public void onPrincipalLeft(PrincipalLeftEvent event) {
 *     // Archive or deactivate domain records, do NOT delete
 *     patientService.archivePatientRecord(
 *         event.getTenantId(), event.getPrincipalId());
 * }
 * }</pre>
 *
 * <h3>Idempotency</h3>
 * <p>Consumers should use {@code principalId} for idempotent processing.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see PrincipalJoinedEvent
 */
@SchemaVersion(value = 1, minCompatible = 1)
public class PrincipalLeftEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The principal ID (sys_tenant_principal.id).
     */
    private final Long principalId;

    /**
     * Reason for leaving.
     *
     * <p>Human-readable or code-based reason:
     * <ul>
     *   <li>"VOLUNTARY_EXIT" — Subject chose to leave</li>
     *   <li>"ADMIN_REVOKED" — Admin revoked the principal</li>
     *   <li>"POLICY_VIOLATION" — Removed due to policy violation</li>
     * </ul>
     */
    private final String reason;

    /**
     * When the principal left the tenant.
     */
    private final Instant leftAt;

    /**
     * Creates a new PrincipalLeftEvent.
     *
     * @param tenantId    the tenant ID
     * @param principalId the principal ID (sys_tenant_principal.id)
     * @param reason      reason for leaving
     * @param leftAt      when the principal left
     */
    public PrincipalLeftEvent(Long tenantId, Long principalId, String reason, Instant leftAt) {
        super("brix-platform-tenant", tenantId != null ? String.valueOf(tenantId) : null);
        this.principalId = principalId;
        this.reason = reason;
        this.leftAt = leftAt;
    }

    @Override
    public String getRoutingKey() {
        return "principal." + getTenantId();
    }

    /**
     * Returns the principal ID.
     *
     * @return the principal ID (sys_tenant_principal.id)
     */
    public Long getPrincipalId() {
        return principalId;
    }

    /**
     * Returns the reason for leaving.
     *
     * @return reason string
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns when the principal left the tenant.
     *
     * @return the departure timestamp
     */
    public Instant getLeftAt() {
        return leftAt;
    }
}
