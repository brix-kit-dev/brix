/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.enums.PlatformAdminRole;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

class PlatformMfaLoginSupportTest {

    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
    private final JwtValidator jwtValidator = mock(JwtValidator.class);
    private final JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
    private final TotpCapability totpCapability = mock(TotpCapability.class);
    private final SecretEncryptionCapability secretEncryptionCapability = mock(SecretEncryptionCapability.class);

    @Test
    void verifiesChallengeAndTotpThenIssuesPlatformToken() throws Exception {
        PlatformMfaLoginSupport support = support();
        Identity identity = activeIdentity();
        PlatformAdmin admin = activeAdmin(true);
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identityRepository.findById(1L)).thenReturn(Optional.of(identity));
        when(platformAdminRepository.findByIdentityId(1L)).thenReturn(Optional.of(admin));
        when(secretEncryptionCapability.decryptSecret("encrypted-secret")).thenReturn("plain-secret");
        when(totpCapability.validateCode("plain-secret", "123456")).thenReturn(true);
        when(jwtIssuerCapability.issuePlatformAdminToken(any())).thenReturn("access-token");
        when(jwtIssuerCapability.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        var result = support.verify(new MfaVerifyCommand("challenge", "123456"));

        assertEquals(LoginStatus.COMPLETE, result.status());
        assertEquals("access-token", result.accessToken());
        assertNull(result.refreshToken());
        assertEquals(3600L, result.expiresIn());
        assertEquals("PLATFORM_SUPER_ADMIN", result.primaryRole());
        assertEquals(PlatformPermissions.defaultPermissionsFor("PLATFORM_SUPER_ADMIN"), result.permissions());
        verify(identityRepository).updateLastLogin(any(), any());
    }

    @Test
    void rejectsExpiredOrMalformedChallengeToken() throws Exception {
        PlatformMfaLoginSupport support = support();
        when(jwtValidator.validate("expired")).thenThrow(new JwtValidationException(
                "expired", JwtValidationException.Reason.EXPIRED));

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> support.verify(new MfaVerifyCommand("expired", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_REQUIRED, ex.getErrorCode());
        verify(jwtIssuerCapability, never()).issuePlatformAdminToken(any());
    }

    @Test
    void rejectsUnboundMfaGrant() throws Exception {
        PlatformMfaLoginSupport support = support();
        Identity identity = activeIdentity();
        PlatformAdmin admin = activeAdmin(false);
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identityRepository.findById(1L)).thenReturn(Optional.of(identity));
        when(platformAdminRepository.findByIdentityId(1L)).thenReturn(Optional.of(admin));

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> support.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_SETUP_REQUIRED, ex.getErrorCode());
        verify(jwtIssuerCapability, never()).issuePlatformAdminToken(any());
    }

    @Test
    void rejectsWrongTotpCode() throws Exception {
        PlatformMfaLoginSupport support = support();
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identityRepository.findById(1L)).thenReturn(Optional.of(activeIdentity()));
        when(platformAdminRepository.findByIdentityId(1L)).thenReturn(Optional.of(activeAdmin(true)));
        when(secretEncryptionCapability.decryptSecret("encrypted-secret")).thenReturn("plain-secret");
        when(totpCapability.validateCode("plain-secret", "123456")).thenReturn(false);

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> support.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_REQUIRED, ex.getErrorCode());
        verify(jwtIssuerCapability, never()).issuePlatformAdminToken(any());
    }

    @Test
    void failsClosedWhenRequiredCapabilityIsMissing() {
        PlatformMfaLoginSupport support = new PlatformMfaLoginSupport(
                identityRepository,
                platformAdminRepository,
                Optional.of(jwtValidator),
                Optional.of(jwtIssuerCapability),
                Optional.empty(),
                Optional.of(secretEncryptionCapability));

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> support.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_CAPABILITY_UNAVAILABLE, ex.getErrorCode());
    }

    private PlatformMfaLoginSupport support() {
        return new PlatformMfaLoginSupport(
                identityRepository,
                platformAdminRepository,
                Optional.of(jwtValidator),
                Optional.of(jwtIssuerCapability),
                Optional.of(totpCapability),
                Optional.of(secretEncryptionCapability));
    }

    private static AuthenticatedUser challenge() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId("1");
        user.setTokenType(TokenType.MFA_CHALLENGE);
        user.setTokenRole(TokenRole.PLATFORM_ADMIN);
        user.setScope("PLATFORM");
        user.setPlatformRole("PLATFORM_SUPER_ADMIN");
        return user;
    }

    private static Identity activeIdentity() {
        Identity identity = new Identity();
        identity.setId(1L);
        identity.setEmail("admin@example.invalid");
        identity.setUsername("Admin");
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setTokenVersion(7L);
        identity.setMfaEnabled(true);
        identity.setMfaSecretEncrypted("encrypted-secret");
        return identity;
    }

    private static PlatformAdmin activeAdmin(boolean mfaEnabled) {
        PlatformAdmin admin = new PlatformAdmin(1L, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(2L);
        admin.setMfaEnabled(mfaEnabled);
        return admin;
    }
}
