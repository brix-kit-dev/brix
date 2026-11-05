package io.brix.platform.auth.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;

/**
 * A platform administrator's TOTP login completes in platform-auth, with the identity steps on the identity owner's
 * capability (standard-link plan L3.2).
 */
class PlatformAdminMfaVerificationTest {

    private static final String ROLE = "PLATFORM_SUPER_ADMIN";

    private final IdentityAccountCapability identities = mock(IdentityAccountCapability.class);
    private final JwtValidator jwtValidator = mock(JwtValidator.class);
    private final JwtIssuerCapability issuer = mock(JwtIssuerCapability.class);
    private final PlatformAdminMfaVerification verification =
            new PlatformAdminMfaVerification(identities, issuer, () -> jwtValidator);

    @Test
    void aValidChallengeAndCodeIssueThePlatformToken() throws Exception {
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identities.findIdentityById(1L)).thenReturn(Optional.of(activeIdentity()));
        when(identities.findActivePlatformAdmin(1L)).thenReturn(Optional.of(admin(true)));
        when(identities.verifyTotp(1L, "123456")).thenReturn(true);
        when(issuer.issuePlatformAdminToken(any())).thenReturn("access-token");
        when(issuer.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        var result = verification.verify(new MfaVerifyCommand("challenge", "123456"));

        assertEquals(LoginStatus.COMPLETE, result.status());
        assertEquals("access-token", result.accessToken());
        assertNull(result.refreshToken());
        assertEquals(3600L, result.expiresIn());
        assertEquals(ROLE, result.primaryRole());
        assertEquals(PlatformPermissions.defaultPermissionsFor(ROLE), result.permissions());
        ArgumentCaptor<JwtIssuerCapability.PlatformAdminTokenRequest> request =
                ArgumentCaptor.forClass(JwtIssuerCapability.PlatformAdminTokenRequest.class);
        verify(issuer).issuePlatformAdminToken(request.capture());
        assertEquals(List.of(2L, 1L, 7L), List.of(request.getValue().adminId(), request.getValue().identityId(),
                request.getValue().tokenVersion()));
        verify(identities).recordSuccessfulLogin(1L, null);
    }

    @Test
    void anExpiredOrMalformedChallengeIsRefused() throws Exception {
        when(jwtValidator.validate("expired")).thenThrow(new JwtValidationException(
                "expired", JwtValidationException.Reason.EXPIRED));

        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> verification.verify(new MfaVerifyCommand("expired", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_REQUIRED, failure.getErrorCode());
        verify(identities, never()).verifyTotp(any(), anyString());
        verify(issuer, never()).issuePlatformAdminToken(any());
    }

    @Test
    void aGrantWithoutMfaNeedsTheSetupFirst() throws Exception {
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identities.findIdentityById(1L)).thenReturn(Optional.of(activeIdentity()));
        when(identities.findActivePlatformAdmin(1L)).thenReturn(Optional.of(admin(false)));

        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> verification.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_SETUP_REQUIRED, failure.getErrorCode());
        verify(identities, never()).verifyTotp(any(), anyString());
        verify(issuer, never()).issuePlatformAdminToken(any());
    }

    @Test
    void aWrongOrReusedCodeIsRefusedByTheIdentityOwner() throws Exception {
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identities.findIdentityById(1L)).thenReturn(Optional.of(activeIdentity()));
        when(identities.findActivePlatformAdmin(1L)).thenReturn(Optional.of(admin(true)));
        when(identities.verifyTotp(eq(1L), anyString())).thenReturn(false);

        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> verification.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_REQUIRED, failure.getErrorCode());
        verify(issuer, never()).issuePlatformAdminToken(any());
        verify(identities, never()).recordSuccessfulLogin(any(), any());
    }

    @Test
    void aChallengeForAnotherRoleIsRefused() throws Exception {
        AuthenticatedUser otherRole = challenge();
        otherRole.setPlatformRole("ANOTHER_ROLE");
        when(jwtValidator.validate("challenge")).thenReturn(otherRole);
        when(identities.findIdentityById(1L)).thenReturn(Optional.of(activeIdentity()));
        when(identities.findActivePlatformAdmin(1L)).thenReturn(Optional.of(admin(true)));

        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> verification.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_MFA_REQUIRED, failure.getErrorCode());
        verify(identities, never()).verifyTotp(any(), anyString());
    }

    @Test
    void anInactiveIdentityIsRefusedAsBadCredentials() throws Exception {
        when(jwtValidator.validate("challenge")).thenReturn(challenge());
        when(identities.findIdentityById(1L)).thenReturn(Optional.of(new IdentityRecord(
                1L, "admin@example.invalid", "Admin", null, "DISABLED", false, 7L)));

        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> verification.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_INVALID_CREDENTIALS, failure.getErrorCode());
    }

    @Test
    void withoutAChallengeValidatorVerificationIsUnavailable() {
        PlatformAdminMfaVerification withoutValidator = new PlatformAdminMfaVerification(identities, issuer, () -> null);

        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> withoutValidator.verify(new MfaVerifyCommand("challenge", "123456")));

        assertEquals(AuthFlowException.CODE_CAPABILITY_UNAVAILABLE, failure.getErrorCode());
    }

    @Test
    void aCodeThatIsNotSixDigitsIsRefusedBeforeAnything() {
        AuthFlowException failure = assertThrows(AuthFlowException.class,
                () -> verification.verify(new MfaVerifyCommand("challenge", "12ab56")));

        assertEquals(AuthFlowException.CODE_MFA_REQUIRED, failure.getErrorCode());
        verify(identities, never()).findIdentityById(any());
    }

    private static AuthenticatedUser challenge() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId("1");
        user.setTokenType(TokenType.MFA_CHALLENGE);
        user.setTokenRole(TokenRole.PLATFORM_ADMIN);
        user.setScope("PLATFORM");
        user.setPlatformRole(ROLE);
        return user;
    }

    private static IdentityRecord activeIdentity() {
        return new IdentityRecord(1L, "admin@example.invalid", "Admin", null, "ACTIVE", false, 7L);
    }

    private static PlatformAdminRecord admin(boolean mfaEnabled) {
        return new PlatformAdminRecord(2L, 1L, ROLE, mfaEnabled);
    }
}
