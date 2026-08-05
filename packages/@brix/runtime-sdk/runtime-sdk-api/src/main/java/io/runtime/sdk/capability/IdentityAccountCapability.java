/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.sdk.capability;

import java.time.Instant;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Global identity account capability.
 *
 * <p>This narrow contract owns identity credentials, token versioning, login
 * lockout state, and platform-admin grant lookup. It intentionally excludes
 * tenant membership/principalship state.</p>
 *
 * @since 3.2.0
 */
@Since("3.2.0")
public interface IdentityAccountCapability {

    Optional<IdentityRecord> findIdentityByEmail(String email);

    Optional<IdentityRecord> findIdentityById(Long id);

    void updatePasswordHash(Long identityId, String newPasswordHash);

    void incrementTokenVersion(Long identityId);

    long getTokenVersion(Long identityId);

    LoginFailureRecord recordFailedLogin(
            Long identityId, int maxAttempts, int lockMinutes, String clientIp);

    void recordSuccessfulLogin(Long identityId, String clientIp);

    boolean unlockExpiredLoginLock(Long identityId, Instant now);

    Optional<PlatformAdminRecord> findActivePlatformAdmin(Long identityId);

    /**
     * Identity record containing credential state. Sensitive fields such as
     * {@code passwordHash} are server-side only and must never be exposed.
     */
    record IdentityRecord(
            Long id,
            String email,
            String username,
            String passwordHash,
            String status,
            boolean passwordMustChange,
            long tokenVersion
    ) {
        @Override
        public String toString() {
            return "IdentityRecord{id=%s,email=%s,username=%s,status=%s,passwordMustChange=%s,tokenVersion=%s}"
                    .formatted(id, email, username, status, passwordMustChange, tokenVersion);
        }
    }

    record LoginFailureRecord(int failedLoginCount, boolean locked, java.time.Instant lockedUntil) {
    }

    record PlatformAdminRecord(Long adminId, Long identityId, String adminRole, boolean mfaEnabled) {
    }
}
