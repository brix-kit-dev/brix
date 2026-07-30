/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.auth.flow.MfaLoginSupport;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

/** Owner-side support for completing platform administrator TOTP login. */
public class PlatformMfaLoginSupport implements MfaLoginSupport {

    private static final String PLATFORM_SCOPE = "PLATFORM";

    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final Optional<JwtValidator> jwtValidator;
    private final Optional<JwtIssuerCapability> jwtIssuerCapability;
    private final Optional<TotpCapability> totpCapability;
    private final Optional<SecretEncryptionCapability> secretEncryptionCapability;

    public PlatformMfaLoginSupport(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            Optional<JwtValidator> jwtValidator,
            Optional<JwtIssuerCapability> jwtIssuerCapability,
            Optional<TotpCapability> totpCapability,
            Optional<SecretEncryptionCapability> secretEncryptionCapability) {
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.jwtValidator = jwtValidator == null ? Optional.empty() : jwtValidator;
        this.jwtIssuerCapability = jwtIssuerCapability == null ? Optional.empty() : jwtIssuerCapability;
        this.totpCapability = totpCapability == null ? Optional.empty() : totpCapability;
        this.secretEncryptionCapability =
                secretEncryptionCapability == null ? Optional.empty() : secretEncryptionCapability;
    }

    @Override
    @Transactional
    public LoginResult verify(MfaVerifyCommand command) {
        validateCommand(command);
        JwtValidator validator = jwtValidator.orElseThrow(PlatformMfaLoginSupport::capabilityUnavailable);
        JwtIssuerCapability issuer = jwtIssuerCapability.orElseThrow(PlatformMfaLoginSupport::capabilityUnavailable);
        TotpCapability totp = totpCapability.orElseThrow(PlatformMfaLoginSupport::capabilityUnavailable);
        SecretEncryptionCapability encryption =
                secretEncryptionCapability.orElseThrow(PlatformMfaLoginSupport::capabilityUnavailable);

        AuthenticatedUser challenge = validateChallenge(validator, command.challengeToken());
        Long identityId = parseIdentityId(challenge.getUserId());
        Identity identity = identityRepository.findById(identityId)
                .filter(i -> i.getStatus() == IdentityStatus.ACTIVE)
                .orElseThrow(() -> new AuthFlowException(
                        AuthFlowException.CODE_INVALID_CREDENTIALS,
                        "Invalid MFA challenge or code"));
        PlatformAdmin admin = platformAdminRepository.findByIdentityId(identityId)
                .filter(PlatformAdmin::isActive)
                .orElseThrow(() -> new AuthFlowException(
                        AuthFlowException.CODE_INVALID_CREDENTIALS,
                        "Invalid MFA challenge or code"));

        requireChallengeMatchesGrant(challenge, admin);
        requireMfaBound(identity, admin);

        String secret = encryption.decryptSecret(identity.getMfaSecretEncrypted());
        if (!totp.validateCode(secret, command.otpCode())) {
            throw invalidMfa();
        }

        String adminRole = admin.getRole().name();
        List<String> permissions = PlatformPermissions.defaultPermissionsFor(adminRole);
        String accessToken = issuer.issuePlatformAdminToken(new JwtIssuerCapability.PlatformAdminTokenRequest(
                admin.getId(),
                identity.getId(),
                identity.getEmail(),
                identity.getUsername(),
                adminRole,
                permissions,
                identity.getTokenVersion()));
        identityRepository.updateLastLogin(identity.getId(), OffsetDateTime.now());

        return new LoginResult(
                LoginStatus.COMPLETE,
                accessToken,
                null,
                issuer.getAccessTokenExpirationSeconds(),
                null,
                null,
                identity.getId(),
                identity.getUsername(),
                identity.getEmail(),
                adminRole,
                List.of(adminRole),
                permissions,
                false,
                false);
    }

    private static void validateCommand(MfaVerifyCommand command) {
        if (command == null
                || command.challengeToken() == null
                || command.challengeToken().isBlank()
                || command.otpCode() == null
                || !command.otpCode().matches("\\d{6}")) {
            throw invalidMfa();
        }
    }

    private static AuthenticatedUser validateChallenge(JwtValidator validator, String challengeToken) {
        try {
            AuthenticatedUser challenge = validator.validate(challengeToken);
            if (challenge.getTokenType() != TokenType.MFA_CHALLENGE
                    || challenge.getTokenRole() != TokenRole.PLATFORM_ADMIN
                    || !PLATFORM_SCOPE.equals(challenge.getScope())
                    || challenge.getPlatformRole() == null
                    || challenge.getPlatformRole().isBlank()) {
                throw invalidMfa();
            }
            return challenge;
        } catch (JwtValidationException | IllegalArgumentException e) {
            throw invalidMfa();
        }
    }

    private static Long parseIdentityId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw invalidMfa();
        }
    }

    private static void requireChallengeMatchesGrant(AuthenticatedUser challenge, PlatformAdmin admin) {
        if (!admin.getRole().name().equals(challenge.getPlatformRole())) {
            throw invalidMfa();
        }
    }

    private static void requireMfaBound(Identity identity, PlatformAdmin admin) {
        if (!identity.isMfaEnabled()
                || !admin.isMfaEnabled()
                || identity.getMfaSecretEncrypted() == null
                || identity.getMfaSecretEncrypted().isBlank()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_MFA_SETUP_REQUIRED,
                    "Platform administrator MFA setup is required.");
        }
    }

    private static AuthFlowException invalidMfa() {
        return new AuthFlowException(
                AuthFlowException.CODE_MFA_REQUIRED,
                "Invalid MFA challenge or code");
    }

    private static AuthFlowException capabilityUnavailable() {
        return new AuthFlowException(
                AuthFlowException.CODE_CAPABILITY_UNAVAILABLE,
                "MFA/TOTP verification capability is unavailable.");
    }
}
