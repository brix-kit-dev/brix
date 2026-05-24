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
package io.brix.platform.admin.service;

import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.brix.platform.admin.dto.CreateFirstAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.admin.config.PlatformAdminSetupProperties;
import io.brix.platform.admin.service.BootstrapTokenService.ValidBootstrapSession;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.NotificationCapability;

/** Creates the first formal platform super administrator during Bootstrap Stage A. */
@Service
public class BootstrapSetupService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSetupService.class);

    private final BootstrapTokenService bootstrapTokenService;
    private final BootstrapStateRepository bootstrapStateRepository;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final SetupTokenService setupTokenService;
    private final ObjectProvider<NotificationCapability> notificationCapability;
    private final PlatformAdminSetupProperties setupProperties;
    private final AuditService auditService;
    private final IdGenerator idGenerator;

    public BootstrapSetupService(
            BootstrapTokenService bootstrapTokenService,
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            SetupTokenService setupTokenService,
            ObjectProvider<NotificationCapability> notificationCapability,
            PlatformAdminSetupProperties setupProperties,
            AuditService auditService,
            IdGenerator idGenerator) {
        this.bootstrapTokenService = bootstrapTokenService;
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.setupTokenService = setupTokenService;
        this.notificationCapability = notificationCapability;
        this.setupProperties = setupProperties;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CreatePlatformAdminResponse createFirstAdmin(CreateFirstAdminRequest request) {
        ValidBootstrapSession session = bootstrapTokenService.requireCurrentSessionForUpdate();
        BootstrapState state = session.state();

        var activeSuperAdmins = platformAdminRepository.findActiveSuperAdmins();
        if (!activeSuperAdmins.isEmpty()) {
            if (activeSuperAdmins.size() == 1) {
                return reissuePendingFirstAdminSetupLinkIfSameTarget(activeSuperAdmins.get(0), request, state);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "FORMAL_SUPER_ADMIN_ALREADY_EXISTS");
        }
        if (identityRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDENTITY_EMAIL_ALREADY_EXISTS");
        }

        NotificationCapability notifier = notificationCapability.getIfAvailable();
        if (notifier == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SETUP_LINK_NOTIFIER_UNAVAILABLE");
        }

        Identity identity = new Identity(request.email(), request.username());
        identity.setId(idGenerator.nextId());
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        identity.setPasswordHash(null);
        identity.setMfaEnabled(false);
        identityRepository.saveAndFlush(identity);

        PlatformAdmin admin = new PlatformAdmin(identity.getId(), PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(idGenerator.nextId());
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        admin.setMfaEnabled(false);
        admin.setCreatedBy(state.getBootstrapIdentityId());
        admin.setNotes(request.notes());
        platformAdminRepository.saveAndFlush(admin);

        SetupTokenService.IssuedSetupToken setupToken = setupTokenService.issuePlatformAdminSetupToken(
                identity.getId(), state.getBootstrapIdentityId());
        sendSetupLinkOrFail(notifier, identity.getEmail(), setupToken);

        state.consumeSession();
        bootstrapStateRepository.save(state);

        auditService.log(AuditEvent.builder()
                .createdBy(state.getBootstrapIdentityId())
                .action(AuditAction.BOOTSTRAP_ADMIN_CREATED)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(admin.getId()))
                .description("First formal platform super administrator created through bootstrap.")
                .success(true)
                .build());

        return new CreatePlatformAdminResponse(admin.getId(), identity.getId(), true);
    }

    private CreatePlatformAdminResponse reissuePendingFirstAdminSetupLinkIfSameTarget(
            PlatformAdmin admin,
            CreateFirstAdminRequest request,
            BootstrapState state) {
        Identity identity = identityRepository.findById(admin.getIdentityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "FORMAL_SUPER_ADMIN_IDENTITY_MISSING"));
        if (!isRecoverablePendingFirstAdmin(admin, identity) || !sameEmail(identity.getEmail(), request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "FORMAL_SUPER_ADMIN_ALREADY_EXISTS");
        }

        NotificationCapability notifier = requireNotifier();
        SetupTokenService.IssuedSetupToken setupToken = setupTokenService.issuePlatformAdminSetupToken(
                identity.getId(), state.getBootstrapIdentityId());
        sendSetupLinkOrFail(notifier, identity.getEmail(), setupToken);

        state.consumeSession();
        bootstrapStateRepository.save(state);

        auditService.log(AuditEvent.builder()
                .createdBy(state.getBootstrapIdentityId())
                .action(AuditAction.SETUP_TOKEN_ISSUED)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(admin.getId()))
                .description("Pending first formal platform super administrator setup link reissued through bootstrap.")
                .success(true)
                .build());

        return new CreatePlatformAdminResponse(admin.getId(), identity.getId(), true);
    }

    private boolean isRecoverablePendingFirstAdmin(PlatformAdmin admin, Identity identity) {
        return admin.getRole() == PlatformAdminRole.PLATFORM_SUPER_ADMIN
                && admin.getStatus() == PlatformAdminStatus.ACTIVE
                && identity.getStatus() == IdentityStatus.PENDING_SETUP
                && identity.getPasswordHash() == null
                && !identity.isMfaEnabled();
    }

    private NotificationCapability requireNotifier() {
        NotificationCapability notifier = notificationCapability.getIfAvailable();
        if (notifier == null) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "SETUP_LINK_NOTIFIER_UNAVAILABLE");
        }
        return notifier;
    }

    private void sendSetupLinkOrFail(
            NotificationCapability notifier,
            String email,
            SetupTokenService.IssuedSetupToken setupToken) {
        try {
            notifier.sendSetupLink(email, setupProperties.buildSetupUrl(setupToken.token()), setupToken.purpose());
        } catch (RuntimeException ex) {
            log.warn("Platform admin setup-link delivery failed for email={}, purpose={}",
                    email, setupToken.purpose(), ex);
            throw new SetupLinkDeliveryException(ex);
        }
    }

    private static boolean sameEmail(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
