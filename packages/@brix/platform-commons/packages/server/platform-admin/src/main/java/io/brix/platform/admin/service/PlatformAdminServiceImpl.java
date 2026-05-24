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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.admin.dto.ChangeOwnPasswordRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.admin.dto.PlatformAdminDto;
import io.brix.platform.admin.dto.RevokeAdminRequest;
import io.brix.platform.admin.dto.ResetPasswordResponse;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;
import jakarta.persistence.EntityNotFoundException;

/**
 * Production implementation of {@link PlatformAdminService}.
 *
 * <h3>Dependency Graph (all Layer 2A/2C — no enterprise-* imports allowed)</h3>
 * <ul>
 *   <li>{@link PlatformAdminRepository} — persists/loads {@code sys_platform_admin}</li>
 *   <li>{@link IdentityRepository} — persists/loads {@code sys_identity}</li>
 *   <li>{@link AuditService} — MUST be used for all audit writes (never bypass)</li>
 *   <li>{@link PasswordCapability} — BCrypt hash/verify</li>
 *   <li>{@link IdentityTenantCapability} — token_version management</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Service
@Transactional(readOnly = true)
public class PlatformAdminServiceImpl implements PlatformAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminServiceImpl.class);

    private final PlatformAdminRepository adminRepository;
    private final IdentityRepository identityRepository;
    private final AuditService auditService;
    private final PasswordCapability passwordCapability;
    private final IdentityTenantCapability identityTenantCapability;
        private final SetupTokenService setupTokenService;
        private final NotificationCapability notificationCapability;
        private final IdGenerator idGenerator;
        private final io.brix.platform.admin.config.PlatformAdminSetupProperties setupProperties;
        private final TotpCapability totpCapability;
        private final SecretEncryptionCapability secretEncryptionCapability;

    public PlatformAdminServiceImpl(
            PlatformAdminRepository adminRepository,
            IdentityRepository identityRepository,
            AuditService auditService,
            PasswordCapability passwordCapability,
                        IdentityTenantCapability identityTenantCapability,
                        SetupTokenService setupTokenService,
                        NotificationCapability notificationCapability,
                        IdGenerator idGenerator,
                        io.brix.platform.admin.config.PlatformAdminSetupProperties setupProperties,
                        TotpCapability totpCapability,
                        SecretEncryptionCapability secretEncryptionCapability) {
        this.adminRepository = adminRepository;
        this.identityRepository = identityRepository;
        this.auditService = auditService;
        this.passwordCapability = passwordCapability;
        this.identityTenantCapability = identityTenantCapability;
                this.setupTokenService = setupTokenService;
                this.notificationCapability = notificationCapability;
                this.idGenerator = idGenerator;
                this.setupProperties = setupProperties;
                this.totpCapability = totpCapability;
                this.secretEncryptionCapability = secretEncryptionCapability;
    }

    // ========================================================================
    // Query Operations
    // ========================================================================

    @Override
    public List<PlatformAdminDto> listAdmins() {
        return adminRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public PlatformAdminDto getAdmin(Long adminId) {
        PlatformAdmin admin = loadAdmin(adminId);
        return toDto(admin);
    }

    // ========================================================================
    // Mutating Operations
    // ========================================================================

    @Override
    @Transactional
    public CreatePlatformAdminResponse createAdmin(CreatePlatformAdminRequest request,
                                                   Long operatorIdentityId) {
        if (identityRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Identity email already exists");
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
        admin.setCreatedBy(operatorIdentityId);
        admin.setNotes(request.notes());
        adminRepository.saveAndFlush(admin);

        SetupTokenService.IssuedSetupToken setupToken = setupTokenService.issue(
                identity.getId(), SetupTokenService.PURPOSE_INITIAL_SETUP, operatorIdentityId);
        sendSetupLinkOrFail(identity.getEmail(), setupToken);

        auditService.log(AuditEvent.builder()
                .createdBy(operatorIdentityId)
                .action(AuditAction.SUPER_ADMIN_CREATED)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(admin.getId()))
                .description("Platform administrator setup link issued.")
                .success(true)
                .build());

        return new CreatePlatformAdminResponse(admin.getId(), identity.getId(), true);
    }

    @Override
    @Transactional
        public void revokeAdmin(Long adminId, RevokeAdminRequest request, Long operatorIdentityId) {
        PlatformAdmin admin = loadAdmin(adminId);

                // Guard: cannot revoke the last active formal platform super admin.
                if (admin.getRole() == PlatformAdminRole.PLATFORM_SUPER_ADMIN) {
            long activeSuperAdmins = adminRepository.findActiveSuperAdmins().size();
            if (activeSuperAdmins <= 1) {
                throw new IllegalStateException(
                                                "Cannot revoke the last active PLATFORM_SUPER_ADMIN account. " +
                                                "Create another formal super admin first.");
            }
        }

                admin.revoke(operatorIdentityId, request.reason());
        adminRepository.save(admin);

        // Invalidate outstanding JWTs by incrementing token_version
        identityTenantCapability.incrementTokenVersion(admin.getIdentityId());

        // Audit
        auditService.log(AuditEvent.builder()
                .createdBy(operatorIdentityId)
                .action(AuditAction.SUPER_ADMIN_REVOKED)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(adminId))
                .description("Revoked platform admin grant. Reason: " + sanitizeReason(request.reason()))
                .success(true)
                .build());

        log.info("Platform admin revoked: adminId={}, revokedBy={}", adminId, operatorIdentityId);
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPassword(Long adminId, Long operatorIdentityId) {
        PlatformAdmin admin = loadAdmin(adminId);
        Identity identity = identityRepository.findById(admin.getIdentityId())
                .orElseThrow(() -> new EntityNotFoundException("Identity not found: " + admin.getIdentityId()));

        setupTokenService.invalidatePreviousFor(identity.getId());
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        identity.setPasswordHash(null);
        identity.setMfaEnabled(false);
        identity.setMfaSecretEncrypted(null);
        identity.setMfaBoundAt(null);
        identityRepository.save(identity);
        admin.setMfaEnabled(false);
        adminRepository.save(admin);
        identityTenantCapability.incrementTokenVersion(identity.getId());

        SetupTokenService.IssuedSetupToken setupToken = setupTokenService.issue(
                identity.getId(), SetupTokenService.PURPOSE_PASSWORD_RESET, operatorIdentityId);
        sendSetupLinkOrFail(identity.getEmail(), setupToken);

        auditService.log(AuditEvent.builder()
                .createdBy(operatorIdentityId)
                .action(AuditAction.SUPER_ADMIN_PASSWORD_RESET)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(adminId))
                .description("Platform administrator password reset setup link issued.")
                .success(true)
                .build());

        return new ResetPasswordResponse(true);
    }

    @Override
    @Transactional
    public void changeOwnPassword(Long identityId, ChangeOwnPasswordRequest request) {
        Identity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Identity not found: " + identityId));

        if (!passwordCapability.verify(request.oldPassword(), identity.getPasswordHash())) {
            throw new AuthFlowCapability.AuthFlowException(
                    AuthFlowCapability.AuthFlowException.CODE_OLD_PASSWORD_MISMATCH,
                    "Current password is incorrect.");
        }
        if (!identity.isMfaEnabled() || identity.getMfaSecretEncrypted() == null) {
            throw new AuthFlowCapability.AuthFlowException(
                    AuthFlowCapability.AuthFlowException.CODE_MFA_SETUP_REQUIRED,
                    "TOTP MFA is required.");
        }
        String secret = secretEncryptionCapability.decryptSecret(identity.getMfaSecretEncrypted());
        if (!totpCapability.validateCode(secret, request.totpCode())) {
            throw new AuthFlowCapability.AuthFlowException(
                    AuthFlowCapability.AuthFlowException.CODE_MFA_REQUIRED,
                    "TOTP code is invalid.");
        }

        PlatformPasswordPolicy.requireCompliant(request.newPassword());
        String newHash = passwordCapability.hash(request.newPassword());
        identity.setPasswordHash(newHash);
        identity.setPasswordMustChange(false);
        identityRepository.saveAndFlush(identity);

        // Invalidate outstanding JWTs
        identityTenantCapability.incrementTokenVersion(identityId);

        // Audit
        auditService.log(AuditEvent.builder()
                .createdBy(identityId)
                .action(AuditAction.SUPER_ADMIN_PASSWORD_CHANGED)
                .resourceType("SELF")
                .resourceId(String.valueOf(identityId))
                .description("Platform admin changed their own password.")
                .success(true)
                .build());

        log.info("Platform admin changed own password: identityId={}", identityId);
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private PlatformAdmin loadAdmin(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Platform admin not found: " + adminId));
    }

    private PlatformAdminDto toDto(PlatformAdmin admin) {
        // Resolve identity for username + email — loaded in same transaction
        return identityRepository.findById(admin.getIdentityId())
                .map(identity -> new PlatformAdminDto(
                        admin.getId(),
                        admin.getIdentityId(),
                        identity.getUsername(),
                        identity.getEmail(),
                        admin.getRole().name(),
                        admin.getStatus().name(),
                        admin.isMfaEnabled(),
                        admin.getNotes(),
                        admin.getCreatedAt()))
                .orElseGet(() -> new PlatformAdminDto(
                        admin.getId(),
                        admin.getIdentityId(),
                        null,
                        null,
                        admin.getRole().name(),
                        admin.getStatus().name(),
                        admin.isMfaEnabled(),
                        admin.getNotes(),
                        admin.getCreatedAt()));
    }

    private void sendSetupLinkOrFail(String email, SetupTokenService.IssuedSetupToken setupToken) {
        try {
            notificationCapability.sendSetupLink(email,
                    setupProperties.buildSetupUrl(setupToken.token()), setupToken.purpose());
        } catch (RuntimeException ex) {
            log.warn("Platform admin setup-link delivery failed for email={}, purpose={}",
                    email, setupToken.purpose(), ex);
            throw new SetupLinkDeliveryException(ex);
        }
    }

    /**
     * Sanitizes a reason string for inclusion in audit descriptions.
     * Ensures it is not null and has a sensible default.
     */
    private String sanitizeReason(String reason) {
        return (reason != null && !reason.isBlank()) ? reason : "(no reason provided)";
    }
}
