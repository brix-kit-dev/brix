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

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.admin.dto.ChangeOwnPasswordRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.admin.dto.DisableAdminRequest;
import io.brix.platform.admin.dto.PlatformAdminDto;
import io.brix.platform.admin.dto.ResetPasswordResponse;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.PasswordCapability;

/**
 * Production implementation of {@link PlatformAdminService}.
 *
 * <h3>Dependency Graph (all Layer 2A/2C — no enterprise-* imports allowed)</h3>
 * <ul>
 *   <li>{@link PlatformAdminRepository} — persists/loads {@code sys_platform_admin}</li>
 *   <li>{@link IdentityRepository} — persists/loads {@code sys_identity}</li>
 *   <li>{@link AuditService} — MUST be used for all audit writes (never bypass)</li>
 *   <li>{@link PasswordGeneratorService} — cryptographically secure temp-password generation</li>
 *   <li>{@link PasswordCapability} — BCrypt hash/verify</li>
 *   <li>{@link IdentityTenantCapability} — token_version management</li>
 *   <li>{@link IdGenerator} — Snowflake ID generation for new entities</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Service
@Transactional(readOnly = true)
public class PlatformAdminServiceImpl implements PlatformAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminServiceImpl.class);

    /** Temporary password validity window after an admin reset. */
    private static final int TEMP_PASSWORD_VALIDITY_HOURS = 24;

    /** Maximum failed-login attempts before account lockout. */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** Lockout duration in minutes. */
    private static final int LOCK_DURATION_MINUTES = 15;

    private final PlatformAdminRepository adminRepository;
    private final IdentityRepository identityRepository;
    private final AuditService auditService;
    private final PasswordGeneratorService passwordGenerator;
    private final PasswordCapability passwordCapability;
    private final IdentityTenantCapability identityTenantCapability;
    private final IdGenerator idGenerator;

    public PlatformAdminServiceImpl(
            PlatformAdminRepository adminRepository,
            IdentityRepository identityRepository,
            AuditService auditService,
            PasswordGeneratorService passwordGenerator,
            PasswordCapability passwordCapability,
            IdentityTenantCapability identityTenantCapability,
            IdGenerator idGenerator) {
        this.adminRepository = adminRepository;
        this.identityRepository = identityRepository;
        this.auditService = auditService;
        this.passwordGenerator = passwordGenerator;
        this.passwordCapability = passwordCapability;
        this.identityTenantCapability = identityTenantCapability;
        this.idGenerator = idGenerator;
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
        // Guard: email uniqueness
        if (identityRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "An identity with email '" + request.email() + "' already exists.");
        }

        // Generate temp password FIRST — R-10: never store in logs or audit reason
        String tempPassword = passwordGenerator.generate();

        // Create the Identity record
        Identity identity = new Identity(request.email(), request.username());
        identity.setId(idGenerator.nextId());
        identity.setPasswordHash(passwordCapability.hash(tempPassword));
        identity.verifyEmail();          // starts as ACTIVE; no email confirmation for admin accounts
        identity.requirePasswordChange(); // operator must rotate on first login
        identityRepository.save(identity);

        // Create the PlatformAdmin record
        PlatformAdminRole role = PlatformAdminRole.valueOf(request.role());
        PlatformAdmin admin = new PlatformAdmin(identity.getId(), role);
        admin.setId(idGenerator.nextId());
        admin.setStatus(MemberStatus.ACTIVE);
        admin.setNotes(request.notes());
        admin.setCreatedBy(operatorIdentityId);
        admin.markTempPasswordIssued(OffsetDateTime.now().plusHours(TEMP_PASSWORD_VALIDITY_HOURS));
        adminRepository.save(admin);

        // Audit — R-10: description MUST NOT mention the password value
        auditService.log(AuditEvent.builder()
                .createdBy(operatorIdentityId)
                .action(AuditAction.SUPER_ADMIN_CREATED)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(admin.getId()))
                .description("Created platform admin account for email: " + request.email()
                        + " with role: " + request.role())
                .success(true)
                .build());

        log.info("Platform admin account created: adminId={}, identityId={}, role={}, createdBy={}",
                admin.getId(), identity.getId(), request.role(), operatorIdentityId);

        return new CreatePlatformAdminResponse(
                admin.getId(),
                identity.getId(),
                request.username(),
                request.email(),
                request.role(),
                tempPassword   // R-10: sole disclosure point
        );
    }

    @Override
    @Transactional
    public void disableAdmin(Long adminId, DisableAdminRequest request, Long operatorIdentityId) {
        PlatformAdmin admin = loadAdmin(adminId);

        // Guard: cannot disable the last active SUPER_ADMIN
        if (admin.getRole() == PlatformAdminRole.SUPER_ADMIN) {
            long activeSuperAdmins = adminRepository.findActiveSuperAdmins().size();
            if (activeSuperAdmins <= 1) {
                throw new IllegalStateException(
                        "Cannot disable the last active SUPER_ADMIN account. " +
                        "Promote another admin to SUPER_ADMIN first.");
            }
        }

        // Perform the disable
        admin.disable(operatorIdentityId, request.reason());
        adminRepository.save(admin);

        // Invalidate outstanding JWTs by incrementing token_version
        identityTenantCapability.incrementTokenVersion(admin.getIdentityId());

        // Audit
        auditService.log(AuditEvent.builder()
                .createdBy(operatorIdentityId)
                .action(AuditAction.SUPER_ADMIN_DISABLED)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(adminId))
                .description("Disabled platform admin account. Reason: " + sanitizeReason(request.reason()))
                .success(true)
                .build());

        log.info("Platform admin disabled: adminId={}, disabledBy={}", adminId, operatorIdentityId);
    }

    @Override
    @Transactional
    public ResetPasswordResponse resetPassword(Long adminId, Long operatorIdentityId) {
        PlatformAdmin admin = loadAdmin(adminId);
        Long identityId = admin.getIdentityId();

        // Generate temp password — R-10: never log this value
        String tempPassword = passwordGenerator.generate();
        String newHash = passwordCapability.hash(tempPassword);

        // Update password hash and force change-on-next-login
        identityTenantCapability.updatePasswordHash(identityId, newHash);

        // Record temp password expiry on the admin record
        admin.markTempPasswordIssued(OffsetDateTime.now().plusHours(TEMP_PASSWORD_VALIDITY_HOURS));
        adminRepository.save(admin);

        // Invalidate outstanding JWTs
        identityTenantCapability.incrementTokenVersion(identityId);

        // Audit — R-10: description MUST NOT mention the password value
        auditService.log(AuditEvent.builder()
                .createdBy(operatorIdentityId)
                .action(AuditAction.SUPER_ADMIN_PASSWORD_RESET)
                .resourceType("PLATFORM_ADMIN")
                .resourceId(String.valueOf(adminId))
                .description("Temporary password issued for platform admin account.")
                .success(true)
                .build());

        log.info("Password reset for platform admin: adminId={}, resetBy={}", adminId, operatorIdentityId);

        return new ResetPasswordResponse(tempPassword); // R-10: sole disclosure point
    }

    @Override
    @Transactional
    public void changeOwnPassword(Long identityId, ChangeOwnPasswordRequest request) {
        Identity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Identity not found: " + identityId));

        // Verify old password
        if (!passwordCapability.verify(request.oldPassword(), identity.getPasswordHash())) {
            throw new AuthFlowCapability.AuthFlowException(
                    AuthFlowCapability.AuthFlowException.CODE_OLD_PASSWORD_MISMATCH,
                    "Current password is incorrect.");
        }

        // Update password hash and clear force-change flag
        String newHash = passwordCapability.hash(request.newPassword());
        identityTenantCapability.updatePasswordHash(identityId, newHash);

        // Invalidate outstanding JWTs
        identityTenantCapability.incrementTokenVersion(identityId);

        // Clear temp-password expiry if applicable
        adminRepository.findByIdentityId(identityId).ifPresent(admin -> {
            admin.clearTempPassword();
            adminRepository.save(admin);
        });

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

    /**
     * Sanitizes a reason string for inclusion in audit descriptions.
     * Ensures it is not null and has a sensible default.
     */
    private String sanitizeReason(String reason) {
        return (reason != null && !reason.isBlank()) ? reason : "(no reason provided)";
    }
}
