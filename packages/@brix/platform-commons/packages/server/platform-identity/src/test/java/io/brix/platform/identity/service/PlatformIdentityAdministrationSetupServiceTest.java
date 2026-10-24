/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.SetupToken;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.internal.CompletePlatformSetupCommand;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.SecretHashing;
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

    /**
     * FIRST_OWNER invitees set up on the hosted setup page of the identity protocol surface
     * (identity-auth BRX-IA-ONBOARD-002, M1 slice 3); the platform setup path no longer takes
     * their tokens.
     */
    @Test
    void tenantFirstOwnerSetupTokensAreNotAcceptedOnThePlatformSetupPath() {
        String rawToken = "tenant-owner-setup-token";
        SetupToken token = setupToken(77L, SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP);
        Identity identity = pendingIdentity(77L);
        when(setupTokenRepository.findByTokenHash(SecretHashing.sha256Base64Url(rawToken)))
            .thenReturn(Optional.of(token));
        when(identityRepository.findById(77L)).thenReturn(Optional.of(identity));

        var service = service();

        assertThrows(IllegalArgumentException.class, () -> service.validateSetupToken(rawToken));
        assertThrows(IllegalArgumentException.class, () -> service.initTotp(rawToken));
        assertThrows(IllegalArgumentException.class, () -> service.completeSetup(new CompletePlatformSetupCommand(
            rawToken, "challenge", "Password!2026", "123456")));
        assertEquals(IdentityStatus.PENDING_SETUP, identity.getStatus());
        assertNull(token.getUsedAt());
        verify(identityRepository, never()).save(any());
        verify(bootstrapCompletionListener, never()).completeIfEligible(any());
        verify(eventPublisher, never()).publishEvent(any());
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
            auditService);
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
