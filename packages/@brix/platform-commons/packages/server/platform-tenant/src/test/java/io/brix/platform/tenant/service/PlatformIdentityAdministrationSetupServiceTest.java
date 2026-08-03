/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.internal.CompletePlatformSetupCommand;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

class PlatformIdentityAdministrationSetupServiceTest {

    private final SetupTokenRepository setupTokenRepository = org.mockito.Mockito.mock(SetupTokenRepository.class);
    private final IdentityRepository identityRepository = org.mockito.Mockito.mock(IdentityRepository.class);
    private final PlatformAdminRepository platformAdminRepository = org.mockito.Mockito.mock(PlatformAdminRepository.class);
    private final PasswordCapability passwordCapability = org.mockito.Mockito.mock(PasswordCapability.class);
    private final TotpCapability totpCapability = org.mockito.Mockito.mock(TotpCapability.class);
    private final SecretEncryptionCapability encryptionCapability =
        org.mockito.Mockito.mock(SecretEncryptionCapability.class);
    private final BootstrapCompletionListener bootstrapCompletionListener =
        org.mockito.Mockito.mock(BootstrapCompletionListener.class);
    private final ApplicationEventPublisher eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
    private final FirstOwnerInvitationService firstOwnerInvitationService =
        org.mockito.Mockito.mock(FirstOwnerInvitationService.class);

    @Test
    void tenantFirstOwnerSetupActivatesIdentityWithoutPlatformAdminGrant() {
        String rawToken = "tenant-owner-setup-token";
        SetupToken token = setupToken(77L, SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP);
        Identity identity = pendingIdentity(77L);
        when(setupTokenRepository.findByTokenHash(SecretHashing.sha256Base64Url(rawToken)))
            .thenReturn(Optional.of(token));
        when(identityRepository.findById(77L)).thenReturn(Optional.of(identity));
        when(totpCapability.generateSecret()).thenReturn("SECRET");
        when(encryptionCapability.encryptSecret("SECRET")).thenReturn("encrypted-secret");
        when(encryptionCapability.decryptSecret("encrypted-secret")).thenReturn("SECRET");
        when(totpCapability.validateCode("SECRET", "123456")).thenReturn(true);
        when(passwordCapability.hash("Password!2026")).thenReturn("hashed-password");

        var service = service();
        var challenge = service.initTotp(rawToken);
        var completion = service.completeSetup(new CompletePlatformSetupCommand(
            rawToken,
            challenge.challengeId(),
            "Password!2026",
            "123456"));

        assertTrue(completion.activated());
        assertEquals("hashed-password", identity.getPasswordHash());
        assertEquals(IdentityStatus.ACTIVE, identity.getStatus());
        assertTrue(identity.isEmailVerified());
        assertTrue(identity.isMfaEnabled());
        assertTrue(token.getUsedAt() != null);
        verify(platformAdminRepository, never()).save(any());
        verify(bootstrapCompletionListener, never()).completeIfEligible(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(firstOwnerInvitationService).sendPendingInvitationsAfterSetup(77L);
    }

    private PlatformIdentityAdministrationService service() {
        return new PlatformIdentityAdministrationService(
            setupTokenRepository,
            identityRepository,
            platformAdminRepository,
            Optional.of(passwordCapability),
            Optional.of(totpCapability),
            Optional.of(encryptionCapability),
            bootstrapCompletionListener,
            eventPublisher,
            auditService,
            Optional.of(firstOwnerInvitationService));
    }

    private static SetupToken setupToken(Long identityId, String purpose) {
        SetupToken token = new SetupToken();
        token.setId(10L);
        token.setIdentityId(identityId);
        token.setPurpose(purpose);
        token.setTokenHash(SecretHashing.sha256Base64Url("tenant-owner-setup-token"));
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return token;
    }

    private static Identity pendingIdentity(Long identityId) {
        Identity identity = new Identity("owner@example.invalid", "owner@example.invalid");
        identity.setId(identityId);
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        return identity;
    }
}
