package io.brix.platform.auth.flow;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;

/**
 * Completes a platform administrator's TOTP login: the second step after the password step issued an MFA challenge.
 *
 * <p>platform-auth owns the token steps: it validates the challenge, maps the administrator's role to its
 * permissions and issues the platform administrator token. The identity steps go to their owner through
 * {@link IdentityAccountCapability}: the identity and its platform grant are read there, and the code is checked
 * there, where the TOTP secret stays (identity-auth BRX-IA-CRED-003) and a code is accepted at most once (RFC 6238
 * §5.2). platform-identity therefore no longer implements a platform-auth interface (root BRX-RS-LIFE-010,
 * MESSAGE-001; standard-link plan L3.2).</p>
 */
public final class PlatformAdminMfaVerification implements MfaLoginSupport {

    private static final String PLATFORM_SCOPE = "PLATFORM";
    private static final String ACTIVE = "ACTIVE";

    private final IdentityAccountCapability identities;
    private final JwtIssuerCapability issuer;
    private final Supplier<JwtValidator> validator;

    /**
     * Creates the verification.
     *
     * @param identities the identity owner's capability
     * @param issuer the token issuer
     * @param validator the challenge validator, which the servlet adapter provides; absent, every verification
     *     fails as unavailable
     */
    public PlatformAdminMfaVerification(
            IdentityAccountCapability identities,
            JwtIssuerCapability issuer,
            Supplier<JwtValidator> validator) {
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public LoginResult verify(MfaVerifyCommand command) {
        validateCommand(command);
        JwtValidator jwt = validator.get();
        if (jwt == null) {
            throw capabilityUnavailable();
        }
        AuthenticatedUser challenge = validateChallenge(jwt, command.challengeToken());
        Long identityId = parseIdentityId(challenge.getUserId());
        IdentityRecord identity = identities.findIdentityById(identityId)
                .filter(found -> ACTIVE.equals(found.status()))
                .orElseThrow(PlatformAdminMfaVerification::invalidCredentials);
        PlatformAdminRecord admin = identities.findActivePlatformAdmin(identityId)
                .orElseThrow(PlatformAdminMfaVerification::invalidCredentials);
        if (!admin.adminRole().equals(challenge.getPlatformRole())) {
            throw invalidMfa();
        }
        if (!admin.mfaEnabled()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_MFA_SETUP_REQUIRED,
                    "Platform administrator MFA setup is required.");
        }
        if (!identities.verifyTotp(identityId, command.otpCode())) {
            throw invalidMfa();
        }

        String adminRole = admin.adminRole();
        List<String> permissions = PlatformPermissions.defaultPermissionsFor(adminRole);
        String accessToken = issuer.issuePlatformAdminToken(new JwtIssuerCapability.PlatformAdminTokenRequest(
                admin.adminId(),
                identity.id(),
                identity.email(),
                identity.username(),
                adminRole,
                permissions,
                identity.tokenVersion()));
        identities.recordSuccessfulLogin(identityId, null);

        return new LoginResult(
                LoginStatus.COMPLETE,
                accessToken,
                null,
                issuer.getAccessTokenExpirationSeconds(),
                null,
                null,
                identity.id(),
                identity.username(),
                identity.email(),
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

    private static AuthFlowException invalidCredentials() {
        return new AuthFlowException(
                AuthFlowException.CODE_INVALID_CREDENTIALS,
                "Invalid MFA challenge or code");
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
