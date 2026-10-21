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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.identity.dto.AuditEvent;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.LoginFailureRecord;
import io.runtime.sdk.capability.IdentityAccountCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * Identity-owned implementation for global credentials and platform-admin grants.
 *
 * <p>Other platform modules still call this capability directly instead of through a runtime
 * entry, so every method opens the identity owner's own unit of work. A caller's transaction is
 * suspended for the duration of the call (root blueprint BRX-RS-DATA-029).</p>
 */
public class IdentityAccountCapabilityImpl implements IdentityAccountCapability {

    private static final Logger log = LoggerFactory.getLogger(IdentityAccountCapabilityImpl.class);

    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final Optional<AuditService> auditService;
    private final UnitOfWork unitOfWork;

    public IdentityAccountCapabilityImpl(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            Optional<AuditService> auditService,
            UnitOfWork unitOfWork) {
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.auditService = auditService;
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork must not be null");
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
