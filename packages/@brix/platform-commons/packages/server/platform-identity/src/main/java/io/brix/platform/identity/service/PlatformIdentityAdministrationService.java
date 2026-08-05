/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.identity.dto.AuditEvent;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.entity.SetupToken;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.enums.PlatformAdminStatus;
import io.brix.platform.identity.internal.CompletePlatformSetupCommand;
import io.brix.platform.identity.internal.PlatformAdminView;
import io.brix.platform.identity.internal.PlatformIdentityAdministration;
import io.brix.platform.identity.internal.PlatformPageRequest;
import io.brix.platform.identity.internal.PlatformPageView;
import io.brix.platform.identity.internal.PlatformSetupCompletionView;
import io.brix.platform.identity.internal.SetupTokenView;
import io.brix.platform.identity.internal.SetupTotpChallengeView;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.SecretHashing;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Owner-side implementation of platform setup identity operations.
 */
public class PlatformIdentityAdministrationService implements PlatformIdentityAdministration {

    private static final int MAX_PAGE_SIZE = 200;

    private final SetupTokenRepository setupTokenRepository;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final Optional<PasswordCapability> passwordCapability;
    private final Optional<TotpCapability> totpCapability;
    private final Optional<SecretEncryptionCapability> secretEncryptionCapability;
    private final BootstrapCompletionListener bootstrapCompletionListener;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    /**
     * Creates the platform identity administration service.
     */
    public PlatformIdentityAdministrationService(
            SetupTokenRepository setupTokenRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            Optional<PasswordCapability> passwordCapability,
            Optional<TotpCapability> totpCapability,
            Optional<SecretEncryptionCapability> secretEncryptionCapability,
            BootstrapCompletionListener bootstrapCompletionListener,
            ApplicationEventPublisher eventPublisher,
            AuditService auditService) {
        this.setupTokenRepository = setupTokenRepository;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.passwordCapability = passwordCapability;
        this.totpCapability = totpCapability;
        this.secretEncryptionCapability = secretEncryptionCapability;
        this.bootstrapCompletionListener = bootstrapCompletionListener;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public SetupTokenView validateSetupToken(String setupToken) {
        SetupToken token = requireUsableToken(setupToken);
        Identity identity = requireIdentity(token.getIdentityId());
        return new SetupTokenView(
                true,
                identity.getId(),
                identity.getEmail(),
                identity.getUsername(),
                token.getPurpose(),
                token.getExpiresAt());
    }

    @Override
    @Transactional
    public SetupTotpChallengeView initTotp(String setupToken) {
        SetupToken token = requireUsableToken(setupToken);
        Identity identity = requireIdentity(token.getIdentityId());
        TotpCapability totp = totpCapability.orElseThrow(
                () -> new IllegalStateException("TotpCapability is required"));
        SecretEncryptionCapability encryption = secretEncryptionCapability.orElseThrow(
                () -> new IllegalStateException("SecretEncryptionCapability is required"));
        String secret = totp.generateSecret();
        identity.setMfaSecretEncrypted(encryption.encryptSecret(secret));
        identity.setMfaEnabled(false);
        identityRepository.save(identity);
        return new SetupTotpChallengeView(challengeId(setupToken, identity.getId()),
                totp.buildOtpauthUri(identity.getEmail(), secret));
    }

    @Override
    @Transactional
    public PlatformSetupCompletionView completeSetup(CompletePlatformSetupCommand command) {
        SetupToken token = requireUsableToken(command.setupToken());
        Identity identity = requireIdentity(token.getIdentityId());
        if (!challengeId(command.setupToken(), identity.getId()).equals(command.challengeId())) {
            throw new IllegalArgumentException("setup challenge is invalid");
        }
        Optional<PlatformAdmin> admin = platformAdminRepository.findByIdentityId(identity.getId())
                .filter(PlatformAdmin::isActive);
        if (SetupTokenPurposes.INITIAL_SETUP.equals(token.getPurpose()) && admin.isEmpty()) {
            throw new IllegalStateException("platform admin grant is missing");
        }
        if (SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP.equals(token.getPurpose()) && admin.isPresent()) {
            throw new IllegalStateException("tenant first-owner setup token is not valid for platform admin identity");
        }
        PasswordCapability password = passwordCapability.orElseThrow(
                () -> new IllegalStateException("PasswordCapability is required"));
        TotpCapability totp = totpCapability.orElseThrow(
                () -> new IllegalStateException("TotpCapability is required"));
        SecretEncryptionCapability encryption = secretEncryptionCapability.orElseThrow(
                () -> new IllegalStateException("SecretEncryptionCapability is required"));
        String encryptedSecret = identity.getMfaSecretEncrypted();
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new IllegalStateException("TOTP enrollment has not been initialized");
        }
        String secret = encryption.decryptSecret(encryptedSecret);
        if (!totp.validateCode(secret, command.totpCode())) {
            throw new IllegalArgumentException("TOTP code is invalid");
        }

        identity.setPasswordHash(password.hash(command.password()));
        identity.setPasswordMustChange(false);
        identity.verifyEmail();
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setMfaEnabled(true);
        identity.setMfaBoundAt(OffsetDateTime.now());
        identity.setTokenVersion(identity.getTokenVersion() + 1);
        identityRepository.save(identity);
        admin.ifPresent(platformAdmin -> {
            platformAdmin.setMfaEnabled(true);
            platformAdminRepository.save(platformAdmin);
        });
        token.setUsedAt(OffsetDateTime.now());
        setupTokenRepository.save(token);
        writeAudit(identity.getId(), AuditAction.IDENTITY_PASSWORD_SET, "IDENTITY", "Identity password set.");
        writeAudit(identity.getId(), AuditAction.TOTP_BOUND, "IDENTITY", "Identity TOTP bound.");
        writeAudit(identity.getId(), AuditAction.SETUP_TOKEN_USED, "SETUP_TOKEN", "Setup token consumed.");
        writeAudit(identity.getId(), AuditAction.IDENTITY_ACTIVATED, "IDENTITY", "Identity activated.");
        if (SetupTokenPurposes.INITIAL_SETUP.equals(token.getPurpose())) {
            bootstrapCompletionListener.completeIfEligible(identity.getId());
            eventPublisher.publishEvent(new BootstrapCompletionListener.IdentitySetupCompletedEvent(identity.getId()));
        } else if (SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP.equals(token.getPurpose())) {
            eventPublisher.publishEvent(new FirstOwnerInviteeSetupCompletedEvent(identity.getId()));
        }
        return new PlatformSetupCompletionView(true);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformPageView<PlatformAdminView> listPlatformAdmins(PlatformPageRequest request) {
        Page<PlatformAdmin> page = findPlatformAdminPage(
            platformAdminStatus(request.status()),
            searchTerm(request.query()),
            pageable(request, adminSortProperty(request.sortBy())));
        Map<Long, Identity> identities = identityRepository.findAllById(page.getContent().stream()
                .map(PlatformAdmin::getIdentityId)
                .toList())
            .stream()
            .collect(Collectors.toMap(Identity::getId, Function.identity()));
        return new PlatformPageView<>(
            page.getContent().stream()
                .map(admin -> toView(admin, identities.get(admin.getIdentityId())))
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast());
    }

    private Page<PlatformAdmin> findPlatformAdminPage(
            PlatformAdminStatus status,
            String term,
            Pageable pageable) {
        if (term == null) {
            return status == null
                    ? platformAdminRepository.findAll(pageable)
                    : platformAdminRepository.findPlatformAdminPageByStatus(status, pageable);
        }
        return status == null
                ? platformAdminRepository.findPlatformAdminPageByTerm(term, pageable)
                : platformAdminRepository.findPlatformAdminPageByStatusAndTerm(status, term, pageable);
    }

    private SetupToken requireUsableToken(String setupToken) {
        if (setupToken == null || setupToken.isBlank()) {
            throw new IllegalArgumentException("setup token is required");
        }
        SetupToken token = setupTokenRepository.findByTokenHash(SecretHashing.sha256Base64Url(setupToken))
                .orElseThrow(() -> new IllegalArgumentException("setup token is invalid"));
        if (!isSupportedPurpose(token.getPurpose()) || !token.isUsable(OffsetDateTime.now())) {
            throw new IllegalArgumentException("setup token is invalid");
        }
        return token;
    }

    private static boolean isSupportedPurpose(String purpose) {
        return SetupTokenPurposes.INITIAL_SETUP.equals(purpose)
                || SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP.equals(purpose);
    }

    private Identity requireIdentity(Long identityId) {
        Identity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new IllegalArgumentException("identity is missing"));
        if (identity.getStatus() != IdentityStatus.PENDING_SETUP) {
            throw new IllegalArgumentException("identity is not pending setup");
        }
        return identity;
    }

    private void writeAudit(Long actorId, String action, String resourceType, String description) {
        auditService.log(AuditEvent.builder()
                .createdBy(actorId)
                .action(action)
                .resourceType(resourceType)
                .description(description)
                .success(true)
                .build());
    }

    private static String challengeId(String setupToken, Long identityId) {
        return SecretHashing.sha256Base64Url(setupToken + ":" + identityId);
    }

    private static PlatformAdminView toView(PlatformAdmin admin, Identity identity) {
        String role = admin.getRole() == null ? null : admin.getRole().name();
        String status = admin.getStatus() == null ? null : admin.getStatus().name();
        return new PlatformAdminView(
            admin.getId(),
            admin.getIdentityId(),
            identity == null ? "" : identity.getUsername(),
            identity == null ? "" : identity.getEmail(),
            role,
            status,
            admin.isMfaEnabled(),
            admin.getNotes(),
            admin.getCreatedAt());
    }

    private static Pageable pageable(PlatformPageRequest request, String sortProperty) {
        int page = Math.max(0, request.page());
        int size = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        Sort.Direction direction = request.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        return org.springframework.data.domain.PageRequest.of(page, size, Sort.by(direction, sortProperty));
    }

    private static String adminSortProperty(String requested) {
        if (requested == null || requested.isBlank()) {
            return "createdAt";
        }
        return switch (requested) {
            case "adminId", "id" -> "id";
            case "identityId" -> "identityId";
            case "role" -> "role";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            default -> "createdAt";
        };
    }

    private static String searchTerm(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private static PlatformAdminStatus platformAdminStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return PlatformAdminStatus.valueOf(value);
    }
}
