/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.internal.BootstrapSessionCommand;
import io.brix.platform.tenant.internal.BootstrapSessionView;
import io.brix.platform.tenant.internal.BootstrapStatusView;
import io.brix.platform.tenant.internal.CreateFirstPlatformAdminCommand;
import io.brix.platform.tenant.internal.PlatformAdminCreationView;
import io.brix.platform.tenant.internal.PlatformBootstrapAdministration;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

/**
 * Owner-side implementation of the Bootstrap administration contract.
 */
public class PlatformBootstrapAdministrationService implements PlatformBootstrapAdministration {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<String> BOOTSTRAP_PERMISSIONS = List.of(
            "bootstrap:status",
            "bootstrap:session",
            "bootstrap:create-first-admin");

    private final SuperAdminBootstrapProperties properties;
    private final BootstrapStateRepository bootstrapStateRepository;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final SetupTokenRepository setupTokenRepository;
    private final IdGenerator idGenerator;
    private final Optional<JwtIssuerCapability> jwtIssuerCapability;
    private final Optional<NotificationCapability> notificationCapability;
    private final AuditService auditService;

    /**
     * Creates the bootstrap administration service.
     */
    public PlatformBootstrapAdministrationService(
            SuperAdminBootstrapProperties properties,
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            SetupTokenRepository setupTokenRepository,
            IdGenerator idGenerator,
            Optional<JwtIssuerCapability> jwtIssuerCapability,
            Optional<NotificationCapability> notificationCapability,
            AuditService auditService) {
        this.properties = properties;
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.setupTokenRepository = setupTokenRepository;
        this.idGenerator = idGenerator;
        this.jwtIssuerCapability = jwtIssuerCapability;
        this.notificationCapability = notificationCapability;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public BootstrapStatusView status() {
        BootstrapState state = bootstrapStateRepository.findById(BootstrapState.SINGLETON_ID)
                .orElse(null);
        boolean open = state != null
                && !state.isCompleted()
                && platformAdminRepository.countCompletedFormalSuperAdmins() == 0;
        return new BootstrapStatusView(
                open,
                state == null ? null : state.getSetupCodeExpiresAt(),
                state == null ? null : state.getCompletedAt());
    }

    @Override
    @Transactional
    public BootstrapSessionView openSession(BootstrapSessionCommand command) {
        BootstrapState state = lockSingleton();
        if (state.isCompleted() || platformAdminRepository.countCompletedFormalSuperAdmins() > 0) {
            throw new IllegalStateException("bootstrap is closed");
        }
        String configuredCode = trimToNull(properties.getSetupCode());
        if (configuredCode == null || !SecretHashing.sha256Base64Url(configuredCode)
                .equals(SecretHashing.sha256Base64Url(command.setupCode()))) {
            writeAudit(null, AuditAction.SETUP_TOKEN_INVALID, "BOOTSTRAP", "Bootstrap setup code rejected", false);
            throw new IllegalArgumentException("bootstrap setup code is invalid");
        }
        JwtIssuerCapability issuer = jwtIssuerCapability.orElseThrow(
                () -> new IllegalStateException("JwtIssuerCapability is required"));
        long ttlSeconds = Math.max(30L, properties.getBootstrapSessionTtlSeconds());
        String jti = UUID.randomUUID().toString();
        String token = issuer.issueBootstrapSetupToken(new JwtIssuerCapability.BootstrapSetupTokenRequest(
                0L,
                0L,
                "bootstrap@local.invalid",
                "Bootstrap Setup",
                BOOTSTRAP_PERMISSIONS,
                1L,
                ttlSeconds,
                jti));
        state.activateSession(SecretHashing.sha256Base64Url(token), OffsetDateTime.now().plusSeconds(ttlSeconds));
        bootstrapStateRepository.save(state);
        writeAudit(null, AuditAction.BOOTSTRAP_SESSION_OPENED, "BOOTSTRAP", "Bootstrap setup session opened.", true);
        return new BootstrapSessionView("BOOTSTRAP_SETUP", token, ttlSeconds);
    }

    @Override
    @Transactional
    public PlatformAdminCreationView createFirstAdmin(CreateFirstPlatformAdminCommand command) {
        BootstrapState state = lockSingleton();
        if (state.isCompleted() || platformAdminRepository.countCompletedFormalSuperAdmins() > 0) {
            throw new IllegalStateException("bootstrap is closed");
        }
        if (!state.isBootstrapSessionUsable(SecretHashing.sha256Base64Url(command.bootstrapSessionToken()),
                OffsetDateTime.now())) {
            throw new IllegalArgumentException("bootstrap session is invalid");
        }
        if (identityRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("email is already registered");
        }
        NotificationCapability notification = notificationCapability.orElseThrow(
                () -> new IllegalStateException("NotificationCapability is required"));
        String setupBaseUrl = trimToNull(properties.getSetupBaseUrl());
        if (setupBaseUrl == null) {
            throw new IllegalStateException("platform setup base URL is required");
        }

        Identity identity = new Identity(command.email().trim().toLowerCase(), command.username().trim());
        identity.setId(idGenerator.nextId());
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        identity.setPasswordHash(null);
        identity.setMfaEnabled(false);
        identity.verifyEmail();
        identity = identityRepository.save(identity);

        PlatformAdmin admin = new PlatformAdmin(identity.getId(), PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(idGenerator.nextId());
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        admin.setMfaEnabled(false);
        admin.setNotes(command.notes());
        admin = platformAdminRepository.save(admin);

        TokenPair token = issueSetupToken(identity.getId(), null);
        String setupUrl = UriComponentsBuilder.fromUriString(setupBaseUrl)
                .queryParam("token", token.raw())
                .build()
                .toUriString();
        notification.send(new NotificationRequest(
                null,
                identity.getEmail(),
                NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                null,
                Map.of("setupUrl", setupUrl)));

        state.consumeSession();
        bootstrapStateRepository.save(state);
        writeAudit(identity.getId(), AuditAction.BOOTSTRAP_ADMIN_CREATED, "PLATFORM_ADMIN",
                "First formal platform super administrator created.", true);
        writeAudit(identity.getId(), AuditAction.SETUP_TOKEN_ISSUED, "SETUP_TOKEN",
                "Initial platform setup token issued.", true);
        return new PlatformAdminCreationView(admin.getId(), identity.getId(), true);
    }

    private BootstrapState lockSingleton() {
        return bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("bootstrap state singleton is missing"));
    }

    private TokenPair issueSetupToken(Long identityId, Long createdBy) {
        setupTokenRepository.markActiveTokensUsed(identityId, "INITIAL_SETUP", OffsetDateTime.now());
        String raw = randomToken();
        SetupToken token = new SetupToken();
        token.setId(idGenerator.nextId());
        token.setIdentityId(identityId);
        token.setPurpose("INITIAL_SETUP");
        token.setTokenHash(SecretHashing.sha256Base64Url(raw));
        token.setExpiresAt(OffsetDateTime.now().plusHours(24));
        token.setCreatedBy(createdBy);
        setupTokenRepository.save(token);
        return new TokenPair(raw);
    }

    private void writeAudit(Long actorId, String action, String resourceType, String description, boolean success) {
        auditService.log(AuditEvent.builder()
                .createdBy(actorId)
                .action(action)
                .resourceType(resourceType)
                .description(description)
                .success(success)
                .build());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record TokenPair(String raw) {
    }
}
