package io.brix.platform.admin.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.admin.dto.PlatformSetupCompleteRequest;
import io.brix.platform.admin.dto.PlatformSetupTotpInitRequest;
import io.brix.platform.admin.dto.PlatformSetupTotpInitResponse;
import io.brix.platform.admin.dto.PlatformSetupValidateResponse;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.brix.platform.tenant.service.BootstrapCompletionListener;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.TotpCapability;

/** Completes platform-admin setup tokens into active TOTP-protected identities. */
@Service
public class PlatformSetupService {

    private static final String CHALLENGE_KEY_PREFIX = "platform-admin:setup-totp:";

    private final SetupTokenService setupTokenService;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordCapability passwordCapability;
    private final TotpCapability totpCapability;
    private final SecretEncryptionCapability secretEncryptionCapability;
    private final StateStoreCapability stateStoreCapability;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public PlatformSetupService(
            SetupTokenService setupTokenService,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            PasswordCapability passwordCapability,
            TotpCapability totpCapability,
            SecretEncryptionCapability secretEncryptionCapability,
            StateStoreCapability stateStoreCapability,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.setupTokenService = setupTokenService;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.passwordCapability = passwordCapability;
        this.totpCapability = totpCapability;
        this.secretEncryptionCapability = secretEncryptionCapability;
        this.stateStoreCapability = stateStoreCapability;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PlatformSetupValidateResponse validate(String setupToken) {
        SetupToken token = setupTokenService.validate(setupToken);
        Identity identity = loadIdentity(token.getIdentityId());
        return new PlatformSetupValidateResponse(
                true,
                identity.getId(), identity.getEmail(), identity.getUsername(), token.getPurpose(), token.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public PlatformSetupTotpInitResponse initTotp(PlatformSetupTotpInitRequest request) {
        SetupToken token = setupTokenService.validate(request.setupToken());
        Identity identity = loadIdentity(token.getIdentityId());
        String secret = totpCapability.generateSecret();
        String encryptedSecret = secretEncryptionCapability.encryptSecret(secret);
        String challengeId = UUID.randomUUID().toString();
        Duration ttl = Duration.between(OffsetDateTime.now(), token.getExpiresAt()).abs();
        if (OffsetDateTime.now().isBefore(token.getExpiresAt())) {
            ttl = Duration.between(OffsetDateTime.now(), token.getExpiresAt());
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Setup token is expired or already used");
        }
        stateStoreCapability.put(CHALLENGE_KEY_PREFIX + challengeId,
                new TotpSetupChallenge(identity.getId(), token.getPurpose(), encryptedSecret), ttl);
        return new PlatformSetupTotpInitResponse(challengeId,
                totpCapability.buildOtpauthUri(identity.getEmail(), secret));
    }

    @Transactional
    public void complete(PlatformSetupCompleteRequest request) {
        SetupToken token = setupTokenService.validate(request.setupToken());
        Identity identity = loadIdentity(token.getIdentityId());
        PlatformAdmin admin = platformAdminRepository.findByIdentityId(identity.getId())
                .orElseThrow(() -> new EntityNotFoundException("Platform admin not found: " + identity.getId()));
        TotpSetupChallenge challenge = stateStoreCapability
                .get(CHALLENGE_KEY_PREFIX + request.challengeId(), TotpSetupChallenge.class)
                .orElseThrow(() -> new IllegalArgumentException("TOTP setup challenge is expired or invalid"));

        if (!identity.getId().equals(challenge.getIdentityId()) || !token.getPurpose().equals(challenge.getPurpose())) {
            throw new IllegalArgumentException("TOTP setup challenge does not match setup token");
        }
        String secret = secretEncryptionCapability.decryptSecret(challenge.getEncryptedSecret());
        if (!totpCapability.validateCode(secret, request.totpCode())) {
            auditService.log(AuditEvent.builder()
                    .createdBy(identity.getId())
                    .action(AuditAction.TOTP_BOUND)
                    .resourceType("PLATFORM_SETUP")
                    .resourceId(String.valueOf(identity.getId()))
                    .description("Platform administrator setup TOTP verification failed.")
                    .success(false)
                    .errorCode("INVALID_TOTP")
                    .build());
            throw new IllegalArgumentException("TOTP code is invalid");
        }

        PlatformPasswordPolicy.requireCompliant(request.password());
        identity.setPasswordHash(passwordCapability.hash(request.password()));
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setEmailVerified(true);
        identity.setPasswordMustChange(false);
        identity.setMfaSecretEncrypted(challenge.getEncryptedSecret());
        identity.setMfaEnabled(true);
        identity.setMfaBoundAt(OffsetDateTime.now());
        identity.setFailedLoginCount(0);
        identity.setLockedUntil(null);
        identityRepository.saveAndFlush(identity);

        admin.setStatus(PlatformAdminStatus.ACTIVE);
        admin.setMfaEnabled(true);
        platformAdminRepository.saveAndFlush(admin);
        setupTokenService.consume(request.setupToken());
        stateStoreCapability.remove(CHALLENGE_KEY_PREFIX + request.challengeId());

        auditService.log(AuditEvent.builder()
                .createdBy(identity.getId())
            .action(AuditAction.IDENTITY_PASSWORD_SET)
                .resourceType("PLATFORM_SETUP")
                .resourceId(String.valueOf(identity.getId()))
            .description("Platform administrator password set through setup flow.")
            .success(true)
            .build());
        auditService.log(AuditEvent.builder()
            .createdBy(identity.getId())
            .action(AuditAction.TOTP_BOUND)
            .resourceType("PLATFORM_SETUP")
            .resourceId(String.valueOf(identity.getId()))
            .description("Platform administrator TOTP authenticator bound.")
            .success(true)
            .build());
        auditService.log(AuditEvent.builder()
            .createdBy(identity.getId())
            .action(AuditAction.IDENTITY_ACTIVATED)
            .resourceType("IDENTITY")
            .resourceId(String.valueOf(identity.getId()))
            .description("Platform administrator identity activated after setup completion.")
                .success(true)
                .build());
        eventPublisher.publishEvent(new BootstrapCompletionListener.IdentitySetupCompletedEvent(identity.getId()));
    }

    private Identity loadIdentity(Long identityId) {
        return identityRepository.findById(identityId)
                .orElseThrow(() -> new EntityNotFoundException("Identity not found: " + identityId));
    }

    public static class TotpSetupChallenge {
        private Long identityId;
        private String purpose;
        private String encryptedSecret;

        public TotpSetupChallenge() {
        }

        public TotpSetupChallenge(Long identityId, String purpose, String encryptedSecret) {
            this.identityId = identityId;
            this.purpose = purpose;
            this.encryptedSecret = encryptedSecret;
        }

        public Long getIdentityId() {
            return identityId;
        }

        public void setIdentityId(Long identityId) {
            this.identityId = identityId;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }

        public String getEncryptedSecret() {
            return encryptedSecret;
        }

        public void setEncryptedSecret(String encryptedSecret) {
            this.encryptedSecret = encryptedSecret;
        }
    }
}
