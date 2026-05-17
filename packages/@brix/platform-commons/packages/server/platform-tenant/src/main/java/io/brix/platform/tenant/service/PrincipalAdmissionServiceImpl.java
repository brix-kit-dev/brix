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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.enums.AdmissionMode;
import io.brix.platform.tenant.enums.PrincipalStatus;
import io.brix.platform.tenant.enums.PrincipalType;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.event.tenant.PrincipalJoinedEvent;

/**
 * Implementation of {@link PrincipalAdmissionService}.
 *
 * <p>Manages the creation of Subject (C-side) principal relationships.
 * All admission operations are idempotent — duplicate calls for the same
 * (tenantId, identityId) return the existing principal without side effects.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — Subject admission implementation.</p>
 *
 * <h3>Transaction Boundary</h3>
 * <p>Each admission operation runs in a single transaction. The
 * {@link PrincipalJoinedEvent} is published within the transaction scope
 * to ensure consistency (via Outbox pattern if configured).</p>
 *
 * <h3>Concurrency</h3>
 * <p>The unique constraint {@code uk_principal_tenant_identity} on
 * {@code sys_tenant_principal(tenant_id, identity_id)} prevents race
 * conditions when two concurrent requests attempt to admit the same
 * subject. The second request will hit the idempotency path.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Service
public class PrincipalAdmissionServiceImpl implements PrincipalAdmissionService {

    private static final Logger log = LoggerFactory.getLogger(PrincipalAdmissionServiceImpl.class);

    private final TenantPrincipalRepository principalRepository;
    private final IdGenerator idGenerator;
    private final EventBusCapability eventBus;

    public PrincipalAdmissionServiceImpl(TenantPrincipalRepository principalRepository,
                                          IdGenerator idGenerator,
                                          EventBusCapability eventBus) {
        this.principalRepository = principalRepository;
        this.idGenerator = idGenerator;
        this.eventBus = eventBus;
    }

    @Override
    @Transactional
    public TenantPrincipal admit(Long tenantId, Long identityId, PrincipalType principalType,
                                  AdmissionMode admissionMode, String displayName) {
        Assert.notNull(tenantId, "Tenant ID must not be null");
        Assert.notNull(identityId, "Identity ID must not be null");
        Assert.notNull(principalType, "Principal type must not be null");
        Assert.notNull(admissionMode, "Admission mode must not be null");

        // =====================================================================
        // Idempotency: check if principal already exists
        // =====================================================================
        Optional<TenantPrincipal> existing = principalRepository
                .findByTenantIdAndIdentityId(tenantId, identityId);

        if (existing.isPresent()) {
            TenantPrincipal existingPrincipal = existing.get();
            log.debug("Principal already exists: id={}, tenantId={}, identityId={}, status={}",
                    existingPrincipal.getId(), tenantId, identityId, existingPrincipal.getStatus());
            return existingPrincipal;
        }

        // =====================================================================
        // Create new principal
        // =====================================================================
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TenantPrincipal principal = new TenantPrincipal(tenantId, identityId, principalType);
        principal.setId(idGenerator.nextId());
        principal.setStatus(PrincipalStatus.ACTIVE);
        principal.setDisplayName(displayName);
        principal.setJoinedAt(now);

        principal = principalRepository.save(principal);

        log.info("Principal admitted: id={}, tenantId={}, identityId={}, type={}, mode={}",
                principal.getId(), tenantId, identityId, principalType, admissionMode);

        // =====================================================================
        // Publish PrincipalJoinedEvent
        // =====================================================================
        PrincipalJoinedEvent event = new PrincipalJoinedEvent(
                tenantId,
                principal.getId(),
                principalType.name(),
                identityId,
                now.toInstant()
        );
        eventBus.publishIntegration(event);

        log.debug("Published PrincipalJoinedEvent: principalId={}, tenantId={}",
                principal.getId(), tenantId);

        return principal;
    }
}
