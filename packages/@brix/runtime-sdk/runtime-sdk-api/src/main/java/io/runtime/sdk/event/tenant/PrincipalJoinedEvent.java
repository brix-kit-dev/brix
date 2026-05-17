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
 * Integration event published when a principal (Subject) joins a tenant.
 *
 * <p>This event signals that a new C-side (Subject) relationship has been
 * established between an identity and a tenant. Industry plugins should
 * listen to this event to create domain-specific business objects.
 *
 * <h3>B2B2C Actor/Subject Model</h3>
 * <p>In the B2B2C model, principals represent service consumers (C-side).
 * When a principal joins a tenant, the platform publishes this event so
 * that industry plugins can react accordingly:
 * <ul>
 *   <li>Healthcare plugin: Create patient record</li>
 *   <li>Education plugin: Create student enrollment</li>
 *   <li>E-commerce plugin: Create buyer account</li>
 * </ul>
 *
 * <h3>Subscriber Example</h3>
 * <pre>{@code
 * @EventListener
 * public void onPrincipalJoined(PrincipalJoinedEvent event) {
 *     if ("CUSTOMER".equals(event.getPrincipalType())) {
 *         patientService.ensurePatientRecord(
 *             event.getTenantId(), event.getPrincipalId());
 *     }
 * }
 * }</pre>
 *
 * <h3>Idempotency</h3>
 * <p>Consumers should use {@code principalId} for idempotent processing
 * to prevent duplicate business object creation.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see PrincipalLeftEvent
 */
@SchemaVersion(value = 1, minCompatible = 1)
public class PrincipalJoinedEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * The principal ID (sys_tenant_principal.id).
     */
    private final Long principalId;

    /**
     * The principal type: "CUSTOMER" or "GUEST".
     *
     * <p>Subscribers typically filter on this field to decide
     * whether to create domain-specific objects.
     */
    private final String principalType;

    /**
     * The identity ID (sys_identity.id) of the Subject.
     */
    private final Long identityId;

    /**
     * When the principal joined the tenant.
     */
    private final Instant joinedAt;

    /**
     * Creates a new PrincipalJoinedEvent.
     *
     * @param tenantId      the tenant ID
     * @param principalId   the principal ID (sys_tenant_principal.id)
     * @param principalType the principal type ("CUSTOMER" or "GUEST")
     * @param identityId    the identity ID (sys_identity.id)
     * @param joinedAt      when the principal joined
     */
    public PrincipalJoinedEvent(Long tenantId, Long principalId, String principalType,
                                Long identityId, Instant joinedAt) {
        super("brix-platform-tenant", tenantId != null ? String.valueOf(tenantId) : null);
        this.principalId = principalId;
        this.principalType = principalType;
        this.identityId = identityId;
        this.joinedAt = joinedAt;
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
     * Returns the principal type.
     *
     * @return "CUSTOMER" or "GUEST"
     */
    public String getPrincipalType() {
        return principalType;
    }

    /**
     * Returns the identity ID.
     *
     * @return the identity ID (sys_identity.id)
     */
    public Long getIdentityId() {
        return identityId;
    }

    /**
     * Returns when the principal joined the tenant.
     *
     * @return the join timestamp
     */
    public Instant getJoinedAt() {
        return joinedAt;
    }
}
