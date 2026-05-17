/*
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
package io.brix.platform.tenant.service;

import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.enums.AdmissionMode;
import io.brix.platform.tenant.enums.PrincipalType;

/**
 * Service for managing Subject (C-side) admission to tenants.
 *
 * <p>Handles the creation of principal relationships between identities
 * and tenants. Supports three admission modes:
 * <ul>
 *   <li><b>Invite:</b> Tenant actor (admin/staff) adds a subject</li>
 *   <li><b>Self-bind:</b> Subject self-registers or accepts an invite link</li>
 *   <li><b>Business trigger:</b> Business action auto-creates the principal</li>
 * </ul>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — Subject admission service contract.</p>
 *
 * <h3>Idempotency</h3>
 * <p>All admission methods are idempotent. If a principal already exists
 * for the given (tenantId, identityId) combination, the existing principal
 * is returned without modification. This is enforced by the unique constraint
 * {@code uk_principal_tenant_identity} on {@code sys_tenant_principal}.</p>
 *
 * <h3>Event Publishing</h3>
 * <p>On successful first-time admission (not idempotent hit), publishes
 * {@link io.runtime.sdk.event.tenant.PrincipalJoinedEvent} via
 * {@link io.runtime.sdk.capability.EventBusCapability}. Industry plugins
 * listen to this event to create domain-specific business objects.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see io.runtime.sdk.event.tenant.PrincipalJoinedEvent
 */
public interface PrincipalAdmissionService {

    /**
     * Admits a subject to a tenant.
     *
     * <p>Creates a new principal relationship (or returns the existing one
     * if already admitted). The admission mode is recorded for audit
     * traceability.</p>
     *
     * <p>On first-time admission:
     * <ol>
     *   <li>Creates {@code sys_tenant_principal} record (ACTIVE status)</li>
     *   <li>Publishes {@code PrincipalJoinedEvent} for plugin listeners</li>
     * </ol>
     *
     * @param tenantId      the target tenant ID
     * @param identityId    the subject's identity ID (sys_identity.id)
     * @param principalType the principal type (CUSTOMER or GUEST)
     * @param admissionMode how the subject is being admitted
     * @param displayName   optional display name for the principal (nullable)
     * @return the admitted principal (existing or newly created)
     * @throws IllegalArgumentException if tenantId or identityId is null
     */
    TenantPrincipal admit(Long tenantId, Long identityId, PrincipalType principalType,
                          AdmissionMode admissionMode, String displayName);
}
