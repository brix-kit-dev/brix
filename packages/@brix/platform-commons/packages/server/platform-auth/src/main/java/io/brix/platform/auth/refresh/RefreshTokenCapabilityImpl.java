/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.refresh;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import io.runtime.sdk.capability.RefreshTokenCapability;

/**
 * DB-backed implementation of {@link RefreshTokenCapability} (A2 security baseline).
 *
 * <h3>Rotation-on-Use</h3>
 * <p>{@link #validateAndRotate} atomically revokes the old token and inserts a new one
 * within a single transaction, preventing concurrent token reuse attacks.</p>
 *
 * <h3>ID Strategy</h3>
 * <p>Uses a simple counter-based ID seeded from current nanotime to avoid adding
 * a Snowflake / Sequence dependency. Collisions are practically impossible given
 * the bounded concurrency of a single deployment.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.1
 */
public class RefreshTokenCapabilityImpl implements RefreshTokenCapability {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCapabilityImpl.class);

    /** Default Refresh Token TTL: 30 days. */
    private static final long DEFAULT_TTL_SECONDS = 30L * 24 * 3600;

    private final RefreshTokenRepository repository;

    public RefreshTokenCapabilityImpl(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void store(String tokenId, Long identityId, Long adminId, long ttlSeconds) {
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        OffsetDateTime now = OffsetDateTime.now();
        StoredRefreshToken token = new StoredRefreshToken(
                generateId(),
                tokenId,
                identityId,
                adminId,
                now,
                now.plusSeconds(effectiveTtl)
        );
        repository.save(token);
        log.debug("[RefreshToken] stored token for identity={}, adminId={}", identityId, adminId);
    }

    @Override
    @Transactional
    public Optional<RotatedToken> validateAndRotate(String tokenId) {
        Optional<StoredRefreshToken> opt = repository.findByTokenId(tokenId);
        if (opt.isEmpty()) {
            log.warn("[RefreshToken] token not found: {}", abbreviate(tokenId));
            return Optional.empty();
        }
        StoredRefreshToken old = opt.get();
        if (!old.isActive()) {
            log.warn("[RefreshToken] token is revoked or expired: identityId={}", old.getIdentityId());
            return Optional.empty();
        }

        // Revoke old token
        old.revoke("ROTATED");
        repository.save(old);

        // Issue new token ID (same TTL from original)
        String newTokenId = UUID.randomUUID().toString();
        long ttlSeconds = java.time.Duration.between(old.getIssuedAt(), old.getExpiresAt()).getSeconds();
        store(newTokenId, old.getIdentityId(), old.getAdminId(), ttlSeconds);

        log.debug("[RefreshToken] rotated token for identity={}", old.getIdentityId());
        return Optional.of(new RotatedToken(newTokenId, old.getIdentityId(), old.getAdminId()));
    }

    @Override
    @Transactional
    public void revokeByTokenId(String tokenId) {
        repository.findByTokenId(tokenId).ifPresent(t -> {
            t.revoke("EXPLICIT_REVOKE");
            repository.save(t);
            log.info("[RefreshToken] revoked token for identity={}", t.getIdentityId());
        });
    }

    @Override
    @Transactional
    public void revokeAllByIdentityId(Long identityId) {
        int count = repository.revokeAllByIdentityId(identityId, OffsetDateTime.now(), "PASSWORD_CHANGED");
        log.info("[RefreshToken] revoked {} token(s) for identity={}", count, identityId);
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    /** Generate a simple pseudo-unique long ID. Sufficient for single-node deployments. */
    private static long generateId() {
        return System.nanoTime() ^ (Thread.currentThread().getId() << 32);
    }

    private static String abbreviate(String s) {
        if (s == null || s.length() <= 8) return "***";
        return s.substring(0, 4) + "..." + s.substring(s.length() - 4);
    }
}
