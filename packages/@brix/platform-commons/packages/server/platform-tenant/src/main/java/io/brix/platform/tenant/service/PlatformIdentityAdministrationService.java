/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.internal.CompletePlatformSetupCommand;
import io.brix.platform.tenant.internal.PlatformIdentityAdministration;
import io.brix.platform.tenant.internal.PlatformSetupCompletionView;
import io.brix.platform.tenant.internal.SetupTokenView;
import io.brix.platform.tenant.internal.SetupTotpChallengeView;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

/**
 * Owner-side implementation of platform setup identity operations.
 */
public class PlatformIdentityAdministrationService implements PlatformIdentityAdministration {

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
        PlatformAdmin admin = platformAdminRepository.findByIdentityId(identity.getId())
                .filter(PlatformAdmin::isActive)
                .orElseThrow(() -> new IllegalStateException("platform admin grant is missing"));
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
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setMfaEnabled(true);
        identity.setMfaBoundAt(OffsetDateTime.now());
        identity.setTokenVersion(identity.getTokenVersion() + 1);
        identityRepository.save(identity);
        admin.setMfaEnabled(true);
        platformAdminRepository.save(admin);
        token.setUsedAt(OffsetDateTime.now());
        setupTokenRepository.save(token);
        writeAudit(identity.getId(), AuditAction.IDENTITY_PASSWORD_SET, "IDENTITY", "Platform identity password set.");
        writeAudit(identity.getId(), AuditAction.TOTP_BOUND, "IDENTITY", "Platform identity TOTP bound.");
        writeAudit(identity.getId(), AuditAction.SETUP_TOKEN_USED, "SETUP_TOKEN", "Platform setup token consumed.");
        writeAudit(identity.getId(), AuditAction.IDENTITY_ACTIVATED, "IDENTITY", "Platform identity activated.");
        bootstrapCompletionListener.completeIfEligible(identity.getId());
        eventPublisher.publishEvent(new BootstrapCompletionListener.IdentitySetupCompletedEvent(identity.getId()));
        return new PlatformSetupCompletionView(true);
    }

    private SetupToken requireUsableToken(String setupToken) {
        if (setupToken == null || setupToken.isBlank()) {
            throw new IllegalArgumentException("setup token is required");
        }
        SetupToken token = setupTokenRepository.findByTokenHash(SecretHashing.sha256Base64Url(setupToken))
                .orElseThrow(() -> new IllegalArgumentException("setup token is invalid"));
        if (!"INITIAL_SETUP".equals(token.getPurpose()) || !token.isUsable(OffsetDateTime.now())) {
            throw new IllegalArgumentException("setup token is invalid");
        }
        return token;
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
}
