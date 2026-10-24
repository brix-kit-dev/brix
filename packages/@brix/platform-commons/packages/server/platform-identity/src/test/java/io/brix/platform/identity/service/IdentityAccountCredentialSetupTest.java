/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.entity.SetupToken;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.enums.PlatformAdminRole;
import io.brix.platform.identity.enums.PlatformAdminStatus;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.PasswordPolicy;
import io.brix.platform.identity.security.SecretHashing;
import io.runtime.sdk.capability.IdentityAccountCapability.CredentialSetupOutcome;
import io.runtime.sdk.capability.IdentityAccountCapability.CredentialSetupRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.PasswordRejection;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Credential setups of the hosted setup page (identity-auth BRX-IA-ONBOARD-002): only FIRST_OWNER
 * invitees that are still being set up, one Owner transaction, the setup-completed event for the
 * tenant Owner, and RFC 6238 §5.2 for the code that completes it.
 */
class IdentityAccountCredentialSetupTest {

    private static final String RAW_TOKEN = "hosted-setup-token";
    private static final long IDENTITY_ID = 77L;

    private IdentityRepository identityRepository;
    private PlatformAdminRepository platformAdminRepository;
    private SetupTokenRepository setupTokenRepository;
    private TotpCapability totp;
    private SecretEncryptionCapability encryption;
    private ApplicationEventPublisher events;
    private AuditService audit;
    private RecordingUnitOfWork unitOfWork;
    private IdentityAccountCapabilityImpl capability;
    private Identity identity;
    private SetupToken token;

    @BeforeEach
    void setUp() {
        identityRepository = mock(IdentityRepository.class);
        platformAdminRepository = mock(PlatformAdminRepository.class);
        setupTokenRepository = mock(SetupTokenRepository.class);
        totp = mock(TotpCapability.class);
        encryption = mock(SecretEncryptionCapability.class);
        events = mock(ApplicationEventPublisher.class);
        audit = mock(AuditService.class);
        unitOfWork = new RecordingUnitOfWork();
        capability = new IdentityAccountCapabilityImpl(
                identityRepository,
                platformAdminRepository,
                Optional.of(audit),
                unitOfWork,
                Optional.of(totp),
                Optional.of(encryption),
                new IdentityAccountCapabilityImpl.CredentialSetup(
                        setupTokenRepository, new PasswordPolicy(), events));
        identity = new Identity("owner@clinic.test", "owner@clinic.test");
        identity.setId(IDENTITY_ID);
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        token = new SetupToken();
        token.setId(10L);
        token.setIdentityId(IDENTITY_ID);
        token.setPurpose(SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP);
        token.setTokenHash(SecretHashing.sha256Base64Url(RAW_TOKEN));
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        String hash = SecretHashing.sha256Base64Url(RAW_TOKEN);
        when(setupTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(setupTokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));
        when(identityRepository.findById(IDENTITY_ID)).thenReturn(Optional.of(identity));
        when(platformAdminRepository.findByIdentityId(IDENTITY_ID)).thenReturn(Optional.empty());
    }

    @Test
    void aFirstOwnerSetupTokenOpensTheSetupOfItsPendingIdentity() {
        Optional<CredentialSetupRecord> setup = capability.findCredentialSetup(RAW_TOKEN);

        assertTrue(setup.isPresent());
        assertEquals(IDENTITY_ID, setup.get().identityId());
        assertEquals("owner@clinic.test", setup.get().email());
        assertEquals("TENANT_FIRST_OWNER_SETUP", setup.get().purpose());
        assertEquals(List.of("readOnly"), unitOfWork.modes());
    }

    @Test
    void otherPurposesUsedOrExpiredTokensAndSetUpIdentitiesOpenNothing() {
        token.setPurpose(SetupTokenPurposes.INITIAL_SETUP);
        assertTrue(capability.findCredentialSetup(RAW_TOKEN).isEmpty(), "platform administrators set up elsewhere");

        token.setPurpose(SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP);
        token.setUsedAt(OffsetDateTime.now());
        assertTrue(capability.findCredentialSetup(RAW_TOKEN).isEmpty(), "used");

        token.setUsedAt(null);
        token.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertTrue(capability.findCredentialSetup(RAW_TOKEN).isEmpty(), "expired");

        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        identity.setStatus(IdentityStatus.ACTIVE);
        assertTrue(capability.findCredentialSetup(RAW_TOKEN).isEmpty(), "already set up");

        identity.setStatus(IdentityStatus.PENDING_SETUP);
        when(platformAdminRepository.findByIdentityId(IDENTITY_ID)).thenReturn(Optional.of(activeAdmin()));
        assertTrue(capability.findCredentialSetup(RAW_TOKEN).isEmpty(), "holds a platform grant");

        assertTrue(capability.findCredentialSetup("another-token").isEmpty());
        assertTrue(capability.findCredentialSetup(" ").isEmpty());
    }

    @Test
    void newPasswordsAreCheckedAgainstTheSecurityProfileAndTheAccountName() {
        assertEquals(Optional.empty(), capability.rejectNewPassword(IDENTITY_ID, "violet kettle harbour"));
        assertEquals(Optional.of(PasswordRejection.BREACHED), capability.rejectNewPassword(IDENTITY_ID, "password1"));
        assertEquals(Optional.of(PasswordRejection.CONTAINS_ACCOUNT_NAME),
                capability.rejectNewPassword(IDENTITY_ID, "owner of the clinic"));
        assertEquals(Optional.of(PasswordRejection.UNAVAILABLE), capability.rejectNewPassword(404L, "violet kettle harbour"));
    }

    @Test
    void enrollmentKeepsANewEncryptedSecretWithoutBindingTheFactor() {
        when(totp.generateSecret()).thenReturn("SECRET");
        when(encryption.encryptSecret("SECRET")).thenReturn("encrypted");
        when(totp.buildOtpauthUri("owner@clinic.test", "SECRET")).thenReturn("otpauth://totp/Brix:owner");

        assertEquals(Optional.of("otpauth://totp/Brix:owner"), capability.beginTotpEnrollment(RAW_TOKEN));

        assertEquals("encrypted", identity.getMfaSecretEncrypted());
        assertFalse(identity.isMfaEnabled());
        assertEquals(IdentityStatus.PENDING_SETUP, identity.getStatus());
        assertEquals(List.of("required"), unitOfWork.modes());
        verify(setupTokenRepository).findByTokenHashForUpdate(SecretHashing.sha256Base64Url(RAW_TOKEN));
    }

    @Test
    void completingActivatesTheIdentityConsumesTheTokenAndTellsTheTenantOwner() {
        identity.setMfaSecretEncrypted("encrypted");
        when(encryption.decryptSecret("encrypted")).thenReturn("SECRET");
        when(totp.matchTimeStep("SECRET", "123456")).thenReturn(OptionalLong.of(5_000L));

        assertEquals(CredentialSetupOutcome.COMPLETED, capability.completeCredentialSetup(RAW_TOKEN, "new-hash", " 123456 "));

        assertEquals(IdentityStatus.ACTIVE, identity.getStatus());
        assertEquals("new-hash", identity.getPasswordHash());
        assertTrue(identity.isMfaEnabled());
        assertTrue(identity.isEmailVerified());
        assertEquals(5_000L, identity.getMfaLastUsedStep(), "the setup code cannot sign in again");
        assertTrue(token.getUsedAt() != null);
        verify(setupTokenRepository).save(token);
        verify(events).publishEvent(new FirstOwnerInviteeSetupCompletedEvent(IDENTITY_ID));
        verify(audit, org.mockito.Mockito.times(4)).log(any());

        // The consumed token opens nothing any more.
        assertEquals(CredentialSetupOutcome.INVALID_TOKEN, capability.completeCredentialSetup(RAW_TOKEN, "other-hash", "123456"));
        assertEquals("new-hash", identity.getPasswordHash());
    }

    @Test
    void aWrongCodeOrNoEnrollmentChangesNothing() {
        assertEquals(CredentialSetupOutcome.INVALID_CODE, capability.completeCredentialSetup(RAW_TOKEN, "new-hash", "123456"),
                "enrollment not started");

        identity.setMfaSecretEncrypted("encrypted");
        when(encryption.decryptSecret("encrypted")).thenReturn("SECRET");
        when(totp.matchTimeStep("SECRET", "000000")).thenReturn(OptionalLong.empty());
        assertEquals(CredentialSetupOutcome.INVALID_CODE, capability.completeCredentialSetup(RAW_TOKEN, "new-hash", "000000"));

        assertEquals(IdentityStatus.PENDING_SETUP, identity.getStatus());
        assertNull(identity.getPasswordHash());
        assertNull(token.getUsedAt());
        verify(identityRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void withoutSetupSupportNothingIsSetUpAndNoPasswordIsAccepted() {
        IdentityAccountCapabilityImpl plain = new IdentityAccountCapabilityImpl(
                identityRepository, platformAdminRepository, Optional.empty(), unitOfWork,
                Optional.of(totp), Optional.of(encryption));

        assertTrue(plain.findCredentialSetup(RAW_TOKEN).isEmpty());
        assertTrue(plain.beginTotpEnrollment(RAW_TOKEN).isEmpty());
        assertEquals(CredentialSetupOutcome.UNAVAILABLE, plain.completeCredentialSetup(RAW_TOKEN, "hash", "123456"));
        assertEquals(Optional.of(PasswordRejection.UNAVAILABLE), plain.rejectNewPassword(IDENTITY_ID, "violet kettle harbour"));
    }

    private static PlatformAdmin activeAdmin() {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setIdentityId(IDENTITY_ID);
        admin.setRole(PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        return admin;
    }
}
