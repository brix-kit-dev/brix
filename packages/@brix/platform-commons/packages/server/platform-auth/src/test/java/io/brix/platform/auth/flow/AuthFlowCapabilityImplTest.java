package io.brix.platform.auth.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginCommand;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.IdentityTenantCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityTenantCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

class AuthFlowCapabilityImplTest {

    @Test
    void exposesRuntimeCapabilityMetadataForInternalContractBinding() {
        Capability capability = AuthFlowCapabilityImpl.class.getAnnotation(Capability.class);

        assertEquals(io.runtime.sdk.capability.AuthFlowCapability.class, capability.type());
        assertEquals("platform-auth-flow", capability.name());
        assertEquals(CapabilityLevel.CORE, capability.level());
    }

    @Test
    void autoConfigurationExposesAnnotatedImplementationTypeForRuntimeScanning() {
        Method factoryMethod = Arrays.stream(AuthFlowAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("authFlowCapability"))
                .findFirst()
                .orElseThrow();

        assertEquals(AuthFlowCapabilityImpl.class, factoryMethod.getReturnType());
    }

    @Test
    void redactsLoginIdBeforeAuthFailureLogging() {
        assertEquals("a***@e***", AuthFlowCapabilityImpl.redactedLoginId("admin@example.invalid"));
        assertEquals("s***", AuthFlowCapabilityImpl.redactedLoginId("shiqiang"));
        assertEquals("<blank>", AuthFlowCapabilityImpl.redactedLoginId(" "));
        assertEquals("<blank>", AuthFlowCapabilityImpl.redactedLoginId(null));
    }

    @Test
    void pendingSetupIdentityCannotLoginThroughPlatformAuth() {
        IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
        PasswordCapability passwordCapability = mock(PasswordCapability.class);
        JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
        AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                identityTenantCapability,
                passwordCapability,
                jwtIssuerCapability,
                null);

        when(identityTenantCapability.findIdentityByEmail("bootstrap@example.invalid"))
                .thenReturn(java.util.Optional.of(new IdentityRecord(
                        1L,
                        "bootstrap@example.invalid",
                        "Bootstrap Setup",
                        null,
                        "PENDING_SETUP",
                        false,
                        0L)));

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> authFlow.loginPlatformAdmin(
                        new LoginCommand("bootstrap@example.invalid", "anything", "127.0.0.1")));

        assertEquals(AuthFlowException.CODE_PENDING_SETUP, ex.getErrorCode());
        verify(passwordCapability, never()).verify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activePlatformAdminReceivesMfaChallengeInsteadOfAccessToken() {
        IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
        PasswordCapability passwordCapability = mock(PasswordCapability.class);
        JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
        AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                identityTenantCapability,
                passwordCapability,
                jwtIssuerCapability,
                null);

        when(identityTenantCapability.findIdentityByEmail("admin@example.invalid"))
                .thenReturn(java.util.Optional.of(new IdentityRecord(
                        1L,
                        "admin@example.invalid",
                        "Admin",
                        "hash",
                        "ACTIVE",
                        false,
                        7L)));
        when(passwordCapability.verify("password", "hash")).thenReturn(true);
        when(identityTenantCapability.findActivePlatformAdmin(1L))
                .thenReturn(java.util.Optional.of(new PlatformAdminRecord(
                        2L, 1L, "PLATFORM_SUPER_ADMIN", true)));
        when(jwtIssuerCapability.issuePlatformMfaChallengeToken(any()))
                .thenReturn("mfa-challenge");
        when(jwtIssuerCapability.getIdentityTokenExpirationSeconds()).thenReturn(300L);

        LoginResult result = authFlow.loginPlatformAdmin(
                new LoginCommand("admin@example.invalid", "password", "127.0.0.1"));

        assertEquals(LoginStatus.MFA_REQUIRED, result.status());
        assertEquals("mfa-challenge", result.identityToken());
        verify(jwtIssuerCapability, never()).issuePlatformAdminToken(any());
    }

    @Test
    void wrongPlatformPasswordRecordsFailedLoginAttempt() {
        IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
        PasswordCapability passwordCapability = mock(PasswordCapability.class);
        JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
        AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                identityTenantCapability,
                passwordCapability,
                jwtIssuerCapability,
                null);

        when(identityTenantCapability.findIdentityByEmail("admin@example.invalid"))
                .thenReturn(java.util.Optional.of(new IdentityRecord(
                        1L,
                        "admin@example.invalid",
                        "Admin",
                        "hash",
                        "ACTIVE",
                        false,
                        7L)));
        when(passwordCapability.verify("wrong", "hash")).thenReturn(false);

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> authFlow.loginPlatformAdmin(
                        new LoginCommand("admin@example.invalid", "wrong", "127.0.0.1")));

        assertEquals(AuthFlowException.CODE_INVALID_CREDENTIALS, ex.getErrorCode());
        verify(identityTenantCapability).recordFailedLogin(1L, 5, 15, "127.0.0.1");
        verify(jwtIssuerCapability, never()).issuePlatformMfaChallengeToken(any());
    }

    @Test
    void actorLoginWithoutMembershipReturnsRestrictedIdentityTokenForPreLinking() {
        IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
        PasswordCapability passwordCapability = mock(PasswordCapability.class);
        JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
        AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                identityTenantCapability,
                passwordCapability,
                jwtIssuerCapability,
                null);

        when(identityTenantCapability.findIdentityByEmail("owner@example.invalid"))
                .thenReturn(java.util.Optional.of(new IdentityRecord(
                        1L,
                        "owner@example.invalid",
                        "Owner",
                        "hash",
                        "ACTIVE",
                        false,
                        7L)));
        when(passwordCapability.verify("password", "hash")).thenReturn(true);
        when(identityTenantCapability.getActiveMemberships(1L)).thenReturn(java.util.List.of());
        when(jwtIssuerCapability.issueIdentityToken(any())).thenReturn("identity-token");

        LoginResult result = authFlow.loginActor(
                new LoginCommand("owner@example.invalid", "password", "127.0.0.1"));

        assertEquals(LoginStatus.SELECT_TENANT, result.status());
        assertEquals("identity-token", result.identityToken());
        assertEquals(java.util.List.of(), result.tenantOptions());
        verify(jwtIssuerCapability, never()).issueActorAccessToken(any());
    }

    @Test
    void lockedPlatformIdentityReturnsLockedWithoutPasswordVerification() {
        IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
        PasswordCapability passwordCapability = mock(PasswordCapability.class);
        JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
        AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                identityTenantCapability,
                passwordCapability,
                jwtIssuerCapability,
                null);

        when(identityTenantCapability.findIdentityByEmail("admin@example.invalid"))
                .thenReturn(java.util.Optional.of(new IdentityRecord(
                        1L,
                        "admin@example.invalid",
                        "Admin",
                        "hash",
                        "LOCKED",
                        false,
                        7L)));
        when(identityTenantCapability.unlockExpiredLoginLock(eq(1L), any())).thenReturn(false);

        AuthFlowException ex = assertThrows(AuthFlowException.class,
                () -> authFlow.loginPlatformAdmin(
                        new LoginCommand("admin@example.invalid", "password", "127.0.0.1")));

        assertEquals(AuthFlowException.CODE_ACCOUNT_LOCKED, ex.getErrorCode());
        verify(passwordCapability, never()).verify(any(), any());
    }

        @Test
        void mfaVerifyDelegatesToRegisteredSupport() {
                IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
                PasswordCapability passwordCapability = mock(PasswordCapability.class);
                JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
                MfaLoginSupport mfaLoginSupport = mock(MfaLoginSupport.class);
                AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                                identityTenantCapability,
                                passwordCapability,
                                jwtIssuerCapability,
                                null,
                                null,
                                () -> mfaLoginSupport);
                MfaVerifyCommand command = new MfaVerifyCommand("challenge", "123456");
                LoginResult expected = new LoginResult(
                                LoginStatus.COMPLETE,
                                "access",
                                "refresh",
                                3600L,
                                null,
                                null,
                                1L,
                                "Admin",
                                "admin@example.invalid",
                                "PLATFORM_SUPER_ADMIN",
                                java.util.List.of("PLATFORM_SUPER_ADMIN"),
                                java.util.List.of("platform:admin:list"),
                                false,
                                false);
                when(mfaLoginSupport.verify(command)).thenReturn(expected);

                assertEquals(expected, authFlow.mfaVerify(command));
        }

        @Test
        void mfaVerifyFailsClosedWhenSupportIsAbsent() {
                IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
                PasswordCapability passwordCapability = mock(PasswordCapability.class);
                JwtIssuerCapability jwtIssuerCapability = mock(JwtIssuerCapability.class);
                AuthFlowCapabilityImpl authFlow = new AuthFlowCapabilityImpl(
                                identityTenantCapability,
                                passwordCapability,
                                jwtIssuerCapability,
                                null);

                AuthFlowException ex = assertThrows(AuthFlowException.class,
                                () -> authFlow.mfaVerify(new MfaVerifyCommand("challenge", "123456")));

                assertEquals(AuthFlowException.CODE_CAPABILITY_UNAVAILABLE, ex.getErrorCode());
        }
}
