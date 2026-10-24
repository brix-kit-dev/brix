/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import io.brix.platform.auth.AuditAction;
import io.brix.platform.identity.dto.AuditEvent;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.entity.SetupToken;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.PasswordPolicy;
import io.brix.platform.identity.security.SecretHashing;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.LoginFailureRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Identity-owned implementation for global credentials and platform-admin grants.
 *
 * <p>Other platform modules still call this capability directly instead of through a runtime
 * entry, so every method opens the identity owner's own unit of work. A caller's transaction is
 * suspended for the duration of the call (root blueprint BRX-RS-DATA-029).</p>
 *
 * <p>It also completes the credential setups of the hosted setup page (identity-auth
 * {@code BRX-IA-ONBOARD-002}): FIRST_OWNER invitees set their password and bind TOTP there, never
 * on a {@code /platform/**} page. Platform administrators keep their own setup until M1 slice 4.</p>
 */
public class IdentityAccountCapabilityImpl implements IdentityAccountCapability {

    private static final Logger log = LoggerFactory.getLogger(IdentityAccountCapabilityImpl.class);
    /** Setup purposes the hosted setup page completes. */
    private static final Set<String> HOSTED_SETUP_PURPOSES = Set.of(SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP);

    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final Optional<AuditService> auditService;
    private final UnitOfWork unitOfWork;
    private final Optional<TotpCapability> totpCapability;
    private final Optional<SecretEncryptionCapability> secretEncryptionCapability;
    private final CredentialSetup credentialSetup;

    /**
     * What completing credential setups needs besides the identities: the setup tokens, the
     * password policy and the publisher of the setup-completed event the tenant Owner listens to.
     *
     * @param setupTokens one-time setup tokens of the identity Owner
     * @param passwordPolicy security profile for new passwords
     * @param events publisher of {@link FirstOwnerInviteeSetupCompletedEvent}
     */
    public record CredentialSetup(SetupTokenRepository setupTokens, PasswordPolicy passwordPolicy,
                                  ApplicationEventPublisher events) {
        public CredentialSetup {
            Objects.requireNonNull(setupTokens, "setupTokens must not be null");
            Objects.requireNonNull(passwordPolicy, "passwordPolicy must not be null");
            Objects.requireNonNull(events, "events must not be null");
        }
    }

    /** A usable setup token and the identity it sets up. */
    private record PendingSetup(SetupToken token, Identity identity) {
    }

    public IdentityAccountCapabilityImpl(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            Optional<AuditService> auditService,
            UnitOfWork unitOfWork) {
        this(identityRepository, platformAdminRepository, auditService, unitOfWork,
                Optional.empty(), Optional.empty());
    }

    public IdentityAccountCapabilityImpl(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            Optional<AuditService> auditService,
            UnitOfWork unitOfWork,
            Optional<TotpCapability> totpCapability,
            Optional<SecretEncryptionCapability> secretEncryptionCapability) {
        this(identityRepository, platformAdminRepository, auditService, unitOfWork,
                totpCapability, secretEncryptionCapability, null);
    }

    /**
     * Creates the capability; without {@code credentialSetup} it completes no credential setup
     * and accepts no new password.
     */
    public IdentityAccountCapabilityImpl(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            Optional<AuditService> auditService,
            UnitOfWork unitOfWork,
            Optional<TotpCapability> totpCapability,
            Optional<SecretEncryptionCapability> secretEncryptionCapability,
            CredentialSetup credentialSetup) {
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.auditService = auditService;
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork must not be null");
        this.totpCapability = totpCapability == null ? Optional.empty() : totpCapability;
        this.secretEncryptionCapability =
                secretEncryptionCapability == null ? Optional.empty() : secretEncryptionCapability;
        this.credentialSetup = credentialSetup;
    }

    @Override
    public Optional<CredentialSetupRecord> findCredentialSetup(String setupToken) {
        if (credentialSetup == null || isBlank(setupToken)) {
            return Optional.empty();
        }
        return unitOfWork.readOnly(() -> pendingSetup(setupToken, false).map(found -> new CredentialSetupRecord(
                found.identity().getId(),
                found.identity().getEmail(),
                found.token().getPurpose(),
                found.token().getExpiresAt().toInstant())));
    }

    @Override
    public Optional<PasswordRejection> rejectNewPassword(Long identityId, String password) {
        if (credentialSetup == null || identityId == null) {
            return Optional.of(PasswordRejection.UNAVAILABLE);
        }
        Optional<Identity> identity = unitOfWork.readOnly(() -> identityRepository.findById(identityId));
        return identity.isEmpty()
                ? Optional.of(PasswordRejection.UNAVAILABLE)
                : credentialSetup.passwordPolicy().check(password, identity.get().getEmail(), identity.get().getUsername());
    }

    @Override
    public Optional<String> beginTotpEnrollment(String setupToken) {
        if (!canSetUp() || isBlank(setupToken)) {
            return Optional.empty();
        }
        return unitOfWork.required(() -> pendingSetup(setupToken, true).map(found -> {
            Identity identity = found.identity();
            String secret = totpCapability.get().generateSecret();
            // The factor stays unbound until the setup completes with a code of this secret.
            identity.setMfaSecretEncrypted(secretEncryptionCapability.get().encryptSecret(secret));
            identity.setMfaEnabled(false);
            identityRepository.save(identity);
            return totpCapability.get().buildOtpauthUri(identity.getEmail(), secret);
        }));
    }

    @Override
    public CredentialSetupOutcome completeCredentialSetup(String setupToken, String passwordHash, String totpCode) {
        if (!canSetUp()) {
            return CredentialSetupOutcome.UNAVAILABLE;
        }
        if (isBlank(passwordHash)) {
            throw new IllegalArgumentException("passwordHash is required");
        }
        if (isBlank(setupToken)) {
            return CredentialSetupOutcome.INVALID_TOKEN;
        }
        return unitOfWork.required(() -> {
            Optional<PendingSetup> found = pendingSetup(setupToken, true);
            if (found.isEmpty()) {
                return CredentialSetupOutcome.INVALID_TOKEN;
            }
            Identity identity = found.get().identity();
            String encryptedSecret = identity.getMfaSecretEncrypted();
            if (isBlank(encryptedSecret) || isBlank(totpCode)) {
                return CredentialSetupOutcome.INVALID_CODE;
            }
            OptionalLong step = totpCapability.get().matchTimeStep(
                    secretEncryptionCapability.get().decryptSecret(encryptedSecret), totpCode.trim());
            if (step.isEmpty()) {
                return CredentialSetupOutcome.INVALID_CODE;
            }
            OffsetDateTime now = OffsetDateTime.now();
            identity.setPasswordHash(passwordHash);
            identity.setPasswordMustChange(false);
            identity.verifyEmail();
            identity.setStatus(IdentityStatus.ACTIVE);
            identity.setMfaEnabled(true);
            identity.setMfaBoundAt(now);
            // RFC 6238 §5.2: the code that completed the setup cannot also sign in.
            identity.setMfaLastUsedStep(step.getAsLong());
            identity.setTokenVersion(identity.getTokenVersion() + 1);
            identityRepository.save(identity);
            SetupToken token = found.get().token();
            token.setUsedAt(now);
            credentialSetup.setupTokens().save(token);
            audit(identity.getId(), AuditAction.IDENTITY_PASSWORD_SET, "IDENTITY", "Identity password set.");
            audit(identity.getId(), AuditAction.TOTP_BOUND, "IDENTITY", "Identity TOTP bound.");
            audit(identity.getId(), AuditAction.SETUP_TOKEN_USED, "SETUP_TOKEN", "Setup token consumed.");
            audit(identity.getId(), AuditAction.IDENTITY_ACTIVATED, "IDENTITY", "Identity activated.");
            if (SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP.equals(token.getPurpose())) {
                // After commit the tenant Owner sends the invitation link (FirstOwnerInvitationService).
                credentialSetup.events().publishEvent(new FirstOwnerInviteeSetupCompletedEvent(identity.getId()));
            }
            return CredentialSetupOutcome.COMPLETED;
        });
    }

    /**
     * The setup a raw token opens: a hosted setup purpose, not used, superseded or expired, for an
     * identity still being set up that holds no platform administrator grant.
     */
    private Optional<PendingSetup> pendingSetup(String rawToken, boolean forUpdate) {
        String hash = SecretHashing.sha256Base64Url(rawToken);
        Optional<SetupToken> token = forUpdate
                ? credentialSetup.setupTokens().findByTokenHashForUpdate(hash)
                : credentialSetup.setupTokens().findByTokenHash(hash);
        return token
                .filter(found -> HOSTED_SETUP_PURPOSES.contains(found.getPurpose()))
                .filter(found -> found.isUsable(OffsetDateTime.now()))
                .flatMap(found -> identityRepository.findById(found.getIdentityId())
                        .filter(identity -> identity.getStatus() == IdentityStatus.PENDING_SETUP)
                        .filter(identity -> platformAdminRepository.findByIdentityId(identity.getId())
                                .filter(PlatformAdmin::isActive).isEmpty())
                        .map(identity -> new PendingSetup(found, identity)));
    }

    private boolean canSetUp() {
        return credentialSetup != null && totpCapability.isPresent() && secretEncryptionCapability.isPresent();
    }

    private void audit(Long identityId, String action, String resourceType, String description) {
        auditService.ifPresent(sink -> sink.log(AuditEvent.builder()
                .createdBy(identityId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(String.valueOf(identityId))
                .description(description)
                .success(true)
                .build()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public boolean isTotpEnabled(Long identityId) {
        if (identityId == null) {
            return false;
        }
        return unitOfWork.readOnly(() -> identityRepository.findById(identityId)
                .map(IdentityAccountCapabilityImpl::hasBoundTotp)
                .orElse(false));
    }

    @Override
    public boolean verifyTotp(Long identityId, String code) {
        if (identityId == null || code == null
                || totpCapability.isEmpty() || secretEncryptionCapability.isEmpty()) {
            return false;
        }
        return unitOfWork.required(() -> {
            Identity identity = identityRepository.findById(identityId).orElse(null);
            if (identity == null || identity.getStatus() != IdentityStatus.ACTIVE || !hasBoundTotp(identity)) {
                return false;
            }
            String secret = secretEncryptionCapability.get().decryptSecret(identity.getMfaSecretEncrypted());
            OptionalLong step = totpCapability.get().matchTimeStep(secret, code);
            if (step.isEmpty()) {
                return false;
            }
            // RFC 6238 §5.2: the conditional update accepts each time step once.
            boolean accepted = identityRepository.acceptTotpStep(
                    identityId, step.getAsLong(), OffsetDateTime.now()) == 1;
            if (!accepted) {
                log.warn("[PlatformIdentity] TOTP code reuse rejected for identity={}", identityId);
            }
            return accepted;
        });
    }

    private static boolean hasBoundTotp(Identity identity) {
        return identity.isMfaEnabled()
                && identity.getMfaSecretEncrypted() != null
                && !identity.getMfaSecretEncrypted().isBlank();
    }

    @Override
    public Optional<IdentityRecord> findIdentityByEmail(String email) {
        return unitOfWork.readOnly(() -> identityRepository.findByEmail(email).map(this::toIdentityRecord));
    }

    @Override
    public Optional<IdentityRecord> findIdentityById(Long id) {
        return unitOfWork.readOnly(() -> identityRepository.findById(id).map(this::toIdentityRecord));
    }

    @Override
    public void updatePasswordHash(Long identityId, String newPasswordHash) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("newPasswordHash is required");
        }
        int affected = unitOfWork.required(() -> identityRepository.updatePasswordHash(
                identityId, newPasswordHash, OffsetDateTime.now()));
        if (affected == 0) {
            throw new IllegalArgumentException("Identity not found: id=" + identityId);
        }
        log.info("[PlatformIdentity] password hash updated for identity={} (rows={})",
                identityId, affected);
    }

    @Override
    public void incrementTokenVersion(Long identityId) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        int affected = unitOfWork.required(() -> identityRepository.incrementTokenVersion(
                identityId, OffsetDateTime.now()));
        if (affected == 0) {
            throw new IllegalArgumentException("Identity not found: id=" + identityId);
        }
        log.info("[PlatformIdentity] token_version incremented for identity={}", identityId);
    }

    @Override
    public long getTokenVersion(Long identityId) {
        return unitOfWork.readOnly(() -> identityRepository.findById(identityId)
                .map(Identity::getTokenVersion)
                .orElseThrow(() -> new IllegalArgumentException("Identity not found: id=" + identityId)));
    }

    @Override
    public LoginFailureRecord recordFailedLogin(
            Long identityId, int maxAttempts, int lockMinutes, String clientIp) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        return unitOfWork.required(() -> {
            Identity identity = identityRepository.findById(identityId)
                    .orElseThrow(() -> new IllegalArgumentException("Identity not found: id=" + identityId));
            boolean wasLocked = identity.getStatus() == IdentityStatus.LOCKED;
            identity.recordFailedLogin(maxAttempts, lockMinutes);
            boolean isLocked = identity.getStatus() == IdentityStatus.LOCKED;
            if (isLocked && !wasLocked) {
                identity.setTokenVersion(identity.getTokenVersion() + 1);
            }
            identityRepository.save(identity);
            if (isLocked && !wasLocked) {
                auditService.ifPresent(sink -> sink.log(AuditEvent.builder()
                        .createdBy(identity.getId())
                        .action("IDENTITY_LOCKED")
                        .resourceType("IDENTITY")
                        .resourceId(String.valueOf(identity.getId()))
                        .description("Identity locked after repeated platform login failures.")
                        .success(true)
                        .build()));
            }
            Instant lockedUntil = identity.getLockedUntil() != null ? identity.getLockedUntil().toInstant() : null;
            return new LoginFailureRecord(identity.getFailedLoginCount(), isLocked, lockedUntil);
        });
    }

    @Override
    public void recordSuccessfulLogin(Long identityId, String clientIp) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        unitOfWork.required(() -> {
            Identity identity = identityRepository.findById(identityId)
                    .orElseThrow(() -> new IllegalArgumentException("Identity not found: id=" + identityId));
            identity.recordSuccessfulLogin(clientIp);
            return identityRepository.save(identity);
        });
    }

    @Override
    public boolean unlockExpiredLoginLock(Long identityId, Instant now) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        return unitOfWork.required(() -> {
            Identity identity = identityRepository.findById(identityId)
                    .orElseThrow(() -> new IllegalArgumentException("Identity not found: id=" + identityId));
            if (identity.getStatus() != IdentityStatus.LOCKED
                    || identity.getLockedUntil() == null
                    || now == null
                    || now.isBefore(identity.getLockedUntil().toInstant())) {
                return false;
            }
            identity.setStatus(IdentityStatus.ACTIVE);
            identity.setFailedLoginCount(0);
            identity.setLockedUntil(null);
            identityRepository.save(identity);
            return true;
        });
    }

    @Override
    public Optional<PlatformAdminRecord> findActivePlatformAdmin(Long identityId) {
        if (identityId == null) {
            return Optional.empty();
        }
        return unitOfWork.readOnly(() -> platformAdminRepository.findByIdentityId(identityId)
                .filter(PlatformAdmin::isActive)
                .map(pa -> new PlatformAdminRecord(
                        pa.getId(),
                        pa.getIdentityId(),
                        pa.getRole().name(),
                        pa.isMfaEnabled())));
    }

    private IdentityRecord toIdentityRecord(Identity identity) {
        return new IdentityRecord(
                identity.getId(),
                identity.getEmail(),
                identity.getUsername(),
                identity.getPasswordHash(),
                identity.getStatus().name(),
                identity.isPasswordMustChange(),
                identity.getTokenVersion());
    }
}
