/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.SetupToken;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.SecretHashing;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationRequest;

/**
 * Identity-owned implementation for FIRST_OWNER invitee setup.
 */
public class IdentityFirstOwnerInviteeSetupCapabilityImpl implements FirstOwnerInviteeIdentitySetupCapability {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TENANT_OWNER_SETUP_TEMPLATE = "tenant.owner.setup.initial";

    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final SetupTokenRepository setupTokenRepository;
    private final Optional<NotificationCapability> notificationCapability;
    private final IdGenerator idGenerator;
    private final String setupBaseUrl;

    public IdentityFirstOwnerInviteeSetupCapabilityImpl(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            SetupTokenRepository setupTokenRepository,
            Optional<NotificationCapability> notificationCapability,
            IdGenerator idGenerator,
            String setupBaseUrl) {
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.setupTokenRepository = setupTokenRepository;
        this.notificationCapability = notificationCapability;
        this.idGenerator = idGenerator;
        this.setupBaseUrl = setupBaseUrl;
    }

    @Override
    @Transactional
    public boolean sendSetupIfRequired(
            Long tenantId,
            String inviteeEmail,
            String platformOperatorRef,
            String locale) {
        Identity identity = findOrCreateInviteeIdentity(inviteeEmail);
        if (identity.getStatus() == IdentityStatus.ACTIVE) {
            return false;
        }
        if (identity.getStatus() != IdentityStatus.PENDING_SETUP) {
            throw failure(
                "FIRST_OWNER_INVITEE_IDENTITY_NOT_ELIGIBLE",
                "FIRST_OWNER invitee identity is not eligible for setup");
        }
        if (platformAdminRepository.findByIdentityId(identity.getId()).filter(admin -> admin.isActive()).isPresent()) {
            throw failure(
                "FIRST_OWNER_INVITEE_IDENTITY_NOT_ELIGIBLE",
                "FIRST_OWNER invitee identity must complete platform setup before tenant ownership setup");
        }
        sendSetup(tenantId, identity.getEmail(), issueTenantOwnerSetupToken(identity.getId(), platformOperatorRef), locale);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public String requireIdentityEmail(Long identityId) {
        return identityRepository.findById(identityId)
            .map(Identity::getEmail)
            .orElseThrow(() -> failure(
                "FIRST_OWNER_IDENTITY_NOT_FOUND",
                "Actor identity was not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public String requireActiveIdentityEmail(Long identityId) {
        Identity identity = identityRepository.findById(identityId)
            .orElseThrow(() -> failure(
                "FIRST_OWNER_IDENTITY_NOT_FOUND",
                "Invitee identity was not found"));
        if (identity.getStatus() != IdentityStatus.ACTIVE) {
            throw failure(
                "FIRST_OWNER_INVITEE_IDENTITY_NOT_ACTIVE",
                "FIRST_OWNER invitee identity must complete setup before receiving the invitation");
        }
        return normalizeEmail(identity.getEmail());
    }

    private Identity findOrCreateInviteeIdentity(String inviteeEmail) {
        String email = normalizeEmail(inviteeEmail);
        Identity identity = identityRepository.findByEmail(email).orElse(null);
        if (identity != null) {
            return identity;
        }
        identity = new Identity(email, email);
        identity.setId(idGenerator.nextId());
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        identity.setPasswordHash(null);
        identity.setMfaEnabled(false);
        identity = identityRepository.save(identity);
        identityRepository.flush();
        return identity;
    }

    private String issueTenantOwnerSetupToken(Long identityId, String platformOperatorRef) {
        setupTokenRepository.markActiveTokensUsed(
            identityId,
            SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP,
            OffsetDateTime.now());
        TokenPair tokenPair = newTokenPair();
        SetupToken token = new SetupToken();
        token.setId(idGenerator.nextId());
        token.setIdentityId(identityId);
        token.setPurpose(SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP);
        token.setTokenHash(tokenPair.hash());
        token.setExpiresAt(OffsetDateTime.now().plus(DEFAULT_TTL));
        token.setCreatedBy(identityIdFromOperatorRef(platformOperatorRef).orElse(null));
        setupTokenRepository.save(token);
        return tokenPair.raw();
    }

    private void sendSetup(
            Long tenantId,
            String inviteeEmail,
            String rawToken,
            String locale) {
        NotificationCapability notification = notificationCapability.orElseThrow(
            () -> failure(
                "NOTIFICATION_PROVIDER_MISSING",
                "NotificationCapability is required for FIRST_OWNER invitations"));
        String setupUrl = UriComponentsBuilder.fromUriString(requireBaseUrl(
                setupBaseUrl,
                "FIRST_OWNER_SETUP_BASE_URL_NOT_CONFIGURED",
                "FIRST_OWNER_SETUP_BASE_URL_INVALID",
                "FIRST_OWNER setup base URL"))
            .queryParam("token", rawToken)
            .build(true)
            .toUriString();
        notification.send(new NotificationRequest(
            tenantId,
            inviteeEmail,
            TENANT_OWNER_SETUP_TEMPLATE,
            locale,
            Map.of("setupUrl", setupUrl)));
    }

    private static String requireBaseUrl(
            String value,
            String missingCode,
            String invalidCode,
            String description) {
        if (value == null || value.isBlank()) {
            throw failure(missingCode, description + " is not configured");
        }
        String normalized = value.trim();
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || containsTokenQuery(uri.getRawQuery())) {
                throw failure(invalidCode, description + " is invalid");
            }
            return normalized;
        } catch (URISyntaxException ex) {
            throw failure(invalidCode, description + " is invalid");
        }
    }

    private static boolean containsTokenQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return false;
        }
        for (String parameter : rawQuery.split("&")) {
            String name = parameter;
            int equals = parameter.indexOf('=');
            if (equals >= 0) {
                name = parameter.substring(0, equals);
            }
            if ("token".equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<Long> identityIdFromOperatorRef(String platformOperatorRef) {
        if (platformOperatorRef == null || !platformOperatorRef.startsWith("platform-identity:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(platformOperatorRef.substring("platform-identity:".length())));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static TokenPair newTokenPair() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenPair(raw, SecretHashing.sha256Base64Url(raw));
    }

    private static FirstOwnerInviteeIdentitySetupCapability.IdentitySetupException failure(
            String code,
            String message) {
        return new FirstOwnerInviteeIdentitySetupCapability.IdentitySetupException(code, message);
    }

    private record TokenPair(String raw, String hash) {
    }
}
