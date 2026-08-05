/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.identity.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import io.brix.platform.identity.enums.IdentityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Identity Entity representing a unique user account across the platform.
 *
 * <p>An identity is a global user account that can belong to multiple tenants
 * through tenant membership associations. This is a system-level entity
 * (no tenant_id) as identities exist at the platform level.
 *
 * <h3>Multi-Tenancy Model</h3>
 * <ul>
 *   <li>One identity = one person (globally unique email)</li>
 *   <li>Identity can join multiple tenants</li>
 *   <li>Each membership may have a different tenant role</li>
 * </ul>
 *
 * <h3>Authentication</h3>
 * <ul>
 *   <li>Email-based login (primary)</li>
 *   <li>OAuth integration (via separate table, not in MVP)</li>
 *   <li>Password stored as secure hash (bcrypt/Argon2)</li>
 * </ul>
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Password hash must use strong algorithm (bcrypt recommended)</li>
 *   <li>Email verification required for activation</li>
 *   <li>Failed login tracking for brute force protection</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see IdentityStatus
 */
@Entity(name = "PlatformIdentityIdentity")
@Table(
    name = "sys_identity",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_identity_email", columnNames = "email")
    },
    indexes = {
        @Index(name = "idx_sys_identity_status", columnList = "status"),
        @Index(name = "idx_sys_identity_email_verified", columnList = "email_verified"),
        @Index(name = "idx_sys_identity_last_login", columnList = "last_login_at DESC")
    }
)
public class Identity {

    /**
     * Primary key - Snowflake-generated unique identifier.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Display username.
     *
     * <p>Not unique - users can have the same display name.
     * Used for UI display purposes.
     */
    @Column(name = "username", nullable = false, length = 128)
    private String username;

    /**
     * Email address (unique identifier for authentication).
     *
     * <p>Used for:
     * <ul>
     *   <li>Login authentication</li>
     *   <li>Password reset</li>
     *   <li>Notifications</li>
     * </ul>
     *
     * <p>Must be globally unique across all identities.
     */
    @Column(name = "email", nullable = false, length = 256, unique = true)
    private String email;

    /**
     * Password hash using bcrypt or Argon2.
     *
     * <p>NULL for OAuth-only accounts (users who login via Google, etc.)
     *
     * <p><b>Security:</b> Never store plaintext passwords.
     * Use BCryptPasswordEncoder or similar.
     */
    @Column(name = "password_hash", length = 256)
    private String passwordHash;

    /**
     * Account status.
     *
    * @see IdentityStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IdentityStatus status = IdentityStatus.PENDING_SETUP;

    /**
     * Whether email has been verified.
     *
     * <p>Accounts with unverified email may have limited capabilities.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * Forces a password rotation on the next successful non-platform login.
     *
     * <p>Set to {@code true} by:
     * <ul>
     *   <li>Tenant-side administrative password reset</li>
     *   <li>Future: scheduled password expiry policy</li>
     * </ul>
     *
     * <p>Platform super-admin onboarding does not use this flag. It is modeled by
     * {@link IdentityStatus#PENDING_SETUP}, setup-token validation, password setup,
     * and TOTP binding.
     *
     * @since 3.2.0
     */
    @Column(name = "password_must_change", nullable = false)
    private boolean passwordMustChange = false;

    /**
     * Token version for per-user JWT invalidation (A3 security baseline).
     *
     * <p>Incremented on every password change. The JWT {@code tv} claim must
     * match or exceed this value; otherwise the token is rejected by the
     * security filter. Starting at 1 so the default state is always valid.
     *
     * @since 3.2.1
     */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 1L;

    /**
     * Last successful login timestamp.
     *
     * <p>Used for:
     * <ul>
     *   <li>Security monitoring</li>
     *   <li>Inactive account detection</li>
     *   <li>User activity tracking</li>
     * </ul>
     */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /**
     * Consecutive failed login attempt count.
     *
     * <p>Incremented on every failed password verification. Reset to 0 on successful login.
     * When this value reaches the configured threshold (default: 5), the account is locked
     * for a temporary period via {@link #lockedUntil}.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    /**
     * Temporary account lockout deadline.
     *
     * <p>NULL means the account is not locked. If {@code now() < lockedUntil},
     * the login attempt MUST be rejected before password verification to prevent timing attacks.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    /**
     * IPv4 or IPv6 address of the last successful login.
     *
     * <p>Stored for audit and anomaly-detection purposes only. MUST NOT be used for
     * authorization decisions. Max 64 chars covers both IPv4 and IPv6.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    /**
     * Encrypted TOTP secret used by platform setup and login flows.
     *
     * <p>Stored encrypted by the auth capability. Plaintext secrets must never
     * be logged, returned, or exposed through DTOs.</p>
     *
     * @since 3.2.0
     */
    @Column(name = "mfa_secret_encrypted", length = 512)
    private String mfaSecretEncrypted;

    /** Whether TOTP MFA has been bound for this identity. */
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /** Timestamp when MFA was bound. */
    @Column(name = "mfa_bound_at")
    private OffsetDateTime mfaBoundAt;

    /**
     * Record creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor required by JPA.
     */
    public Identity() {
    }

    /**
     * Creates a new identity with email and username.
     *
     * @param email unique email address
     * @param username display name
     */
    public Identity(String email, String username) {
        this.email = email;
        this.username = username;
        this.status = IdentityStatus.PENDING_SETUP;
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Checks if the account is active and can be used.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == IdentityStatus.ACTIVE;
    }

    /**
     * Checks if the account can perform login.
     *
     * <p>Requires ACTIVE status and (for password login) verified email.
     *
     * @return true if login is allowed
     */
    public boolean canLogin() {
        return isActive() && emailVerified;
    }

    /**
     * Activates the identity account.
     *
     * @throws IllegalStateException if account cannot be activated
     */
    public void activate() {
        if (!status.canBeActivated()) {
            throw new IllegalStateException(
                "Cannot activate identity in status: " + status
            );
        }
        this.status = IdentityStatus.ACTIVE;
    }

    /**
     * Marks email as verified and activates account if pending.
     */
    public void verifyEmail() {
        this.emailVerified = true;
    }

    /**
     * Marks the account as requiring a password change on the next non-platform login.
     *
     * <p>Platform super-admin setup must use {@link IdentityStatus#PENDING_SETUP}
     * instead of this flag.
     *
     * @since 3.2.0
     */
    public void requirePasswordChange() {
        this.passwordMustChange = true;
    }

    /**
     * Clears the forced password-change requirement.
     *
     * <p>Should be called after the user has successfully rotated their
     * password through the change-password endpoint.
     *
     * @since 3.2.0
     */
    public void clearPasswordChangeRequirement() {
        this.passwordMustChange = false;
    }

    /**
     * @return {@code true} if the user must rotate their password on next login
     * @since 3.2.0
     */
    public boolean isPasswordChangeRequired() {
        return passwordMustChange;
    }

    /**
     * Records a successful login.
     */
    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
    }

    /**
     * Records a successful login with IP tracking and resets the failure counter.
     *
     * @param ipAddress the remote IP address of the client (may be null)
     * @since 3.2.0
     */
    public void recordSuccessfulLogin(String ipAddress) {
        this.lastLoginAt = OffsetDateTime.now();
        this.lastLoginIp = ipAddress;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        if (this.status == IdentityStatus.LOCKED) {
            this.status = IdentityStatus.ACTIVE;
        }
    }

    /**
     * Returns {@code true} if the account is currently locked out.
     *
     * <p>A null {@code lockedUntil} always returns {@code false}.
     *
     * @since 3.2.0
     */
    public boolean isLockedOut() {
        return lockedUntil != null && OffsetDateTime.now().isBefore(lockedUntil);
    }

    /**
     * Increments the failed-login counter and locks the account when the threshold is reached.
     *
     * @param maxAttempts  number of failures before lockout (e.g. 5)
     * @param lockMinutes  how long to lock the account in minutes (e.g. 15)
     * @since 3.2.0
     */
    public void recordFailedLogin(int maxAttempts, int lockMinutes) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxAttempts) {
            this.lockedUntil = OffsetDateTime.now().plusMinutes(lockMinutes);
            this.status = IdentityStatus.LOCKED;
        }
    }

    /**
     * Disables the account.
     */
    public void suspend() {
        this.status = IdentityStatus.DISABLED;
    }

    /**
     * Checks if this is an OAuth-only account (no password).
     *
     * @return true if password hash is null
     */
    public boolean isOAuthOnly() {
        return passwordHash == null || passwordHash.isBlank();
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public IdentityStatus getStatus() {
        return status;
    }

    public void setStatus(IdentityStatus status) {
        this.status = status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isPasswordMustChange() {
        return passwordMustChange;
    }

    public void setPasswordMustChange(boolean passwordMustChange) {
        this.passwordMustChange = passwordMustChange;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public String getMfaSecretEncrypted() {
        return mfaSecretEncrypted;
    }

    public void setMfaSecretEncrypted(String mfaSecretEncrypted) {
        this.mfaSecretEncrypted = mfaSecretEncrypted;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public OffsetDateTime getMfaBoundAt() {
        return mfaBoundAt;
    }

    public void setMfaBoundAt(OffsetDateTime mfaBoundAt) {
        this.mfaBoundAt = mfaBoundAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Identity identity = (Identity) o;
        return Objects.equals(id, identity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Identity{" +
               "id=" + id +
               ", email='" + email + '\'' +
               ", username='" + username + '\'' +
               ", status=" + status +
               ", emailVerified=" + emailVerified +
               ", passwordMustChange=" + passwordMustChange +
               '}';
    }
}
