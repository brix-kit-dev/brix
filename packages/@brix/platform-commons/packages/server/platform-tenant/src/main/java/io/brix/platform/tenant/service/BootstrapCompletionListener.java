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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;

/**
 * Closes bootstrap once the first formal super admin has completed setup.
 *
 * <p>The event contract is intentionally small so current and future setup
 * flows can publish it after password setup, MFA binding, or token verification
 * without coupling those flows to bootstrap internals.</p>
 */
@Component
public class BootstrapCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(BootstrapCompletionListener.class);

    private final BootstrapStateRepository bootstrapStateRepository;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final AuditService auditService;

    public BootstrapCompletionListener(
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            AuditService auditService) {
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.auditService = auditService;
    }

    @EventListener
    public void onApplicationEvent(IdentitySetupCompletedEvent event) {
        completeIfEligible(event.identityId());
    }

    @Transactional
    public boolean completeIfEligible(Long identityId) {
        if (identityId == null) {
            return false;
        }
        BootstrapState state = bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)
                .orElse(null);
        if (state == null || state.isCompleted()) {
            return false;
        }

        Identity identity = identityRepository.findById(identityId).orElse(null);
        PlatformAdmin admin = platformAdminRepository.findByIdentityId(identityId).orElse(null);
        if (identity == null || admin == null
                || identity.getStatus() != IdentityStatus.ACTIVE
                || !identity.isMfaEnabled()
                || !admin.isActive()
                || admin.getRole() != PlatformAdminRole.PLATFORM_SUPER_ADMIN) {
            return false;
        }

        admin.setMfaEnabled(true);
        platformAdminRepository.save(admin);
        state.complete(identityId);
        bootstrapStateRepository.save(state);

        deactivateBootstrapAnchors(identityId);
        log.warn("Platform bootstrap permanently closed by identity={}", identityId);
        return true;
    }

    private void deactivateBootstrapAnchors(Long completedByIdentityId) {
        for (PlatformAdmin bootstrapAdmin : platformAdminRepository.findByRole(PlatformAdminRole.BOOTSTRAP)) {
            Long bootstrapIdentityId = bootstrapAdmin.getIdentityId();
            if (bootstrapIdentityId == null) {
                continue;
            }

            identityRepository.findById(bootstrapIdentityId).ifPresent(bootstrapIdentity -> {
                bootstrapIdentity.setStatus(IdentityStatus.DISABLED);
                bootstrapIdentity.setPasswordHash(null);
                bootstrapIdentity.setMfaEnabled(false);
                bootstrapIdentity.setMfaSecretEncrypted(null);
                bootstrapIdentity.setTokenVersion(bootstrapIdentity.getTokenVersion() + 1);
                identityRepository.save(bootstrapIdentity);

                auditService.log(AuditEvent.builder()
                        .createdBy(completedByIdentityId)
                        .action(AuditAction.BOOTSTRAP_ADMIN_DEACTIVATED)
                        .resourceType("BOOTSTRAP")
                        .resourceId(String.valueOf(bootstrapIdentity.getId()))
                        .description("Passwordless bootstrap anchor deactivated after formal platform super admin setup completion.")
                        .success(true)
                        .build());
                auditService.log(AuditEvent.builder()
                        .createdBy(completedByIdentityId)
                        .action(AuditAction.IDENTITY_DISABLED)
                        .resourceType("IDENTITY")
                        .resourceId(String.valueOf(bootstrapIdentity.getId()))
                        .description("Bootstrap identity disabled after formal platform super admin setup completion.")
                        .success(true)
                        .build());
            });
        }
    }

    public record IdentitySetupCompletedEvent(Long identityId) {
    }
}