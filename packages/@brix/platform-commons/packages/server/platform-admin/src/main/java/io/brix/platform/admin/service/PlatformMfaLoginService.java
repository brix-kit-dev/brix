package io.brix.platform.admin.service;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.admin.dto.PlatformLoginResponse;
import io.brix.platform.admin.dto.PlatformTotpLoginRequest;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.RefreshTokenCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

/** Completes platform-admin login after TOTP verification. */
@Service
public class PlatformMfaLoginService {

    private final JwtValidator jwtValidator;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final TotpCapability totpCapability;
    private final SecretEncryptionCapability secretEncryptionCapability;
    private final JwtIssuerCapability jwtIssuerCapability;
    private final RefreshTokenCapability refreshTokenCapability;
    private final AuditService auditService;

    public PlatformMfaLoginService(
            JwtValidator jwtValidator,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            TotpCapability totpCapability,
            SecretEncryptionCapability secretEncryptionCapability,
            JwtIssuerCapability jwtIssuerCapability,
            ObjectProvider<RefreshTokenCapability> refreshTokenCapability,
            AuditService auditService) {
        this.jwtValidator = jwtValidator;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.totpCapability = totpCapability;
        this.secretEncryptionCapability = secretEncryptionCapability;
        this.jwtIssuerCapability = jwtIssuerCapability;
        this.refreshTokenCapability = refreshTokenCapability.getIfAvailable();
        this.auditService = auditService;
    }

    @Transactional
    public PlatformLoginResponse verify(PlatformTotpLoginRequest request, String clientIp) {
        LoginResult result = verifyToLoginResult(request.mfaChallengeToken(), request.totpCode(), clientIp);
        return new PlatformLoginResponse(
                result.status().name(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.primaryRole(),
                result.permissions(),
                result.identityId(),
                result.email(),
                result.displayName(),
                result.mustChangePassword(),
                result.identityToken());
    }

    @Transactional
    public LoginResult verifyToLoginResult(String mfaChallengeToken, String totpCode, String clientIp) {
        AuthenticatedUser challengeUser = validateChallenge(mfaChallengeToken);
        Long identityId = parseIdentityId(challengeUser.getUserId());
        Identity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new EntityNotFoundException("Identity not found: " + identityId));
        PlatformAdmin admin = platformAdminRepository.findByIdentityId(identityId)
                .orElseThrow(() -> new EntityNotFoundException("Platform admin not found: " + identityId));

        if (!identity.isActive() || !admin.isActive() || !identity.isMfaEnabled() || !admin.isMfaEnabled()
                || identity.getMfaSecretEncrypted() == null) {
            throw new AuthFlowException(AuthFlowException.CODE_MFA_SETUP_REQUIRED,
                    "Platform administrator MFA setup is required.");
        }

        String secret = secretEncryptionCapability.decryptSecret(identity.getMfaSecretEncrypted());
        if (!totpCapability.validateCode(secret, totpCode)) {
            auditService.log(AuditEvent.builder()
                    .createdBy(identityId)
                    .action(AuditAction.SUPER_ADMIN_LOGIN_FAILED)
                    .resourceType("PLATFORM_AUTH")
                    .resourceId(String.valueOf(identityId))
                    .description("Platform admin TOTP verification failed.")
                    .clientIp(clientIp)
                    .success(false)
                    .errorCode(AuthFlowException.CODE_MFA_REQUIRED)
                    .build());
            throw new AuthFlowException(AuthFlowException.CODE_MFA_REQUIRED, "TOTP code is invalid.");
        }

        List<String> permissions = PlatformPermissions.defaultPermissionsFor(admin.getRole().name());
        String accessToken = jwtIssuerCapability.issuePlatformAdminToken(
                new JwtIssuerCapability.PlatformAdminTokenRequest(
                        admin.getId(), identity.getId(), identity.getEmail(), identity.getUsername(),
                        admin.getRole().name(), permissions, identity.getTokenVersion()));
        String refreshToken = UUID.randomUUID().toString();
        if (refreshTokenCapability != null) {
            refreshTokenCapability.store(refreshToken, identity.getId(), admin.getId(),
                    jwtIssuerCapability.getAccessTokenExpirationSeconds() * 24);
        }
        identity.recordSuccessfulLogin(clientIp);
        identityRepository.save(identity);

        auditService.log(AuditEvent.builder()
                .createdBy(identityId)
                .action(AuditAction.SUPER_ADMIN_LOGIN_SUCCESS)
                .resourceType("PLATFORM_AUTH")
                .resourceId(String.valueOf(identityId))
                .description("Platform admin TOTP login succeeded.")
                .clientIp(clientIp)
                .success(true)
                .build());

        String primaryRole = admin.getRole().name();
        return new LoginResult(
                LoginStatus.COMPLETE,
                accessToken,
                refreshToken,
                jwtIssuerCapability.getAccessTokenExpirationSeconds(),
                null,
                null,
                identity.getId(),
                identity.getUsername(),
                identity.getEmail(),
                primaryRole,
                List.of(primaryRole),
                permissions,
                false,
                false);
    }

    private AuthenticatedUser validateChallenge(String token) {
        try {
            AuthenticatedUser user = jwtValidator.validate(token);
            if (user.getTokenType() != TokenType.MFA_CHALLENGE) {
                throw new AuthFlowException(AuthFlowException.CODE_MFA_REQUIRED,
                        "MFA challenge token is invalid.");
            }
            return user;
                } catch (JwtValidationException ex) {
            throw new AuthFlowException(AuthFlowException.CODE_MFA_REQUIRED,
                    "MFA challenge token is invalid.", ex);
        }
    }

        private static Long parseIdentityId(String subject) {
                if (subject == null || subject.isBlank()) {
                        throw new AuthFlowException(AuthFlowException.CODE_MFA_REQUIRED,
                                        "MFA challenge token is invalid.");
                }
                try {
                        return Long.parseLong(subject);
                } catch (NumberFormatException ex) {
                        throw new AuthFlowException(AuthFlowException.CODE_MFA_REQUIRED,
                                        "MFA challenge token is invalid.", ex);
        }
        }
}