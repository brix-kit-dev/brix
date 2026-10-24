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
     * Returns whether the identity has a bound TOTP factor.
     *
     * <p>Owners that do not manage TOTP factors keep this default; callers then treat the
     * identity as having no second factor.</p>
     *
     * @param identityId identity ID
     * @return {@code true} when a TOTP factor is bound
     * @since 3.2.0
     */
    default boolean isTotpEnabled(Long identityId) {
        return false;
    }

    /**
     * Verifies a TOTP code against the identity's bound factor inside the identity Owner;
     * the shared secret never leaves the Owner (identity-auth {@code BRX-IA-CRED-003}).
     * A code is accepted at most once (RFC 6238 §5.2): a code whose time step is not later
     * than the last accepted step is rejected.
     *
     * <p>Owners that do not manage TOTP factors keep this default and fail closed.</p>
     *
     * @param identityId identity ID
     * @param code user supplied code
     * @return {@code true} when the code is valid and has not been used before
     * @since 3.2.0
     */
    default boolean verifyTotp(Long identityId, String code) {
        return false;
    }

    /**
     * Finds the credential setup a one-time setup token opens on the hosted setup page of the
     * identity protocol surface (identity-auth {@code BRX-IA-ONBOARD-002}). Only setups of that
     * page are found; a token of another purpose, or one that is used, expired or superseded,
     * finds nothing.
     *
     * <p>Owners that do not manage setup tokens keep this default.</p>
     *
     * @param setupToken raw token of the setup link
     * @return the setup, while the token can still complete it
     * @since 3.2.0
     */
    default Optional<CredentialSetupRecord> findCredentialSetup(String setupToken) {
        return Optional.empty();
    }

    /**
     * Checks a password the identity is about to set against the security profile
     * (identity-auth {@code BRX-IA-CRED-001}): its length, the list of breached passwords and the
     * identity's own account name. There are no composition rules.
     *
     * <p>Owners that do not manage passwords keep this default and accept none.</p>
     *
     * @param identityId identity ID
     * @param password new password
     * @return empty when the password is acceptable, otherwise why it is not
     * @since 3.2.0
     */
    default Optional<PasswordRejection> rejectNewPassword(Long identityId, String password) {
        return Optional.of(PasswordRejection.UNAVAILABLE);
    }

    /**
     * Starts the TOTP enrollment of a credential setup: the Owner generates a new shared secret,
     * keeps it encrypted and returns the {@code otpauth} URI for the authenticator app. A later
     * call replaces the secret; the factor is only bound when the setup completes.
     *
     * <p>Owners that do not manage TOTP factors keep this default.</p>
     *
     * @param setupToken raw token of the setup link
     * @return the {@code otpauth} URI, while the token can still complete its setup
     * @since 3.2.0
     */
    default Optional<String> beginTotpEnrollment(String setupToken) {
        return Optional.empty();
    }

    /**
     * Completes a credential setup in one Owner transaction: verifies the code against the
     * enrolled secret, sets the password, binds the TOTP factor, activates the identity and
     * consumes the token (identity-auth {@code BRX-IA-ONBOARD-002}).
     *
     * <p>Owners that do not manage setup tokens keep this default and complete nothing.</p>
     *
     * @param setupToken raw token of the setup link
     * @param passwordHash hash of the new password from {@link PasswordCapability}, of a password
     *                     {@link #rejectNewPassword(Long, String)} accepted
     * @param totpCode code the authenticator app shows
     * @return the outcome
     * @since 3.2.0
     */
    default CredentialSetupOutcome completeCredentialSetup(String setupToken, String passwordHash, String totpCode) {
        return CredentialSetupOutcome.UNAVAILABLE;
    }

    /**
     * A credential setup the hosted setup page may complete.
     *
     * @param identityId identity being set up
     * @param email its email, shown on the page
     * @param purpose stable purpose of the setup token
     * @param expiresAt when the token expires
     * @since 3.2.0
     */
    record CredentialSetupRecord(Long identityId, String email, String purpose, Instant expiresAt) {
    }

    /**
     * Why a new password is not accepted.
     *
     * @since 3.2.0
     */
    enum PasswordRejection {
        /** Shorter than the security profile allows. */
        TOO_SHORT,
        /** Longer than the security profile allows. */
        TOO_LONG,
        /** On the list of passwords known from breaches, or a trivial sequence. */
        BREACHED,
        /** Contains the identity's account name. */
        CONTAINS_ACCOUNT_NAME,
        /** The Owner cannot check passwords. */
        UNAVAILABLE
    }

    /**
     * Result of completing a credential setup.
     *
     * @since 3.2.0
     */
    enum CredentialSetupOutcome {
        /** The identity is active with its password and TOTP factor. */
        COMPLETED,
        /** The token no longer opens a setup. */
        INVALID_TOKEN,
        /** The code does not match the enrolled secret, or no enrollment was started. */
        INVALID_CODE,
        /** The Owner cannot complete setups. */
        UNAVAILABLE
    }

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
