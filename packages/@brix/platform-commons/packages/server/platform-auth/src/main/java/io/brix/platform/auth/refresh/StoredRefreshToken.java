/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.refresh;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code auth_refresh_token} table.
 *
 * <p>Maps persistent Refresh Token records used for revocation and rotation
 * (A2 security baseline). Tokens are stored as opaque UUIDs; the actual
 * secret value is never recoverable from this record (it is the {@code tokenId}
 * itself, which acts as a bearer credential).
 *
 * <h3>Layer Placement</h3>
 * <p>Layer 2C (platform-auth) — implementation detail, not exposed to plugins.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.1
 */
@Entity
@Table(
    name = "auth_refresh_token",
    indexes = {
        @Index(name = "idx_auth_refresh_token_identity", columnList = "identity_id"),
        @Index(name = "idx_auth_refresh_token_expires",  columnList = "expires_at")
    }
)
public class StoredRefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Opaque UUID known to the client. Must never be logged in full. */
    @Column(name = "token_id", nullable = false, length = 64, unique = true, updatable = false)
    private String tokenId;

    @Column(name = "identity_id", nullable = false, updatable = false)
    private Long identityId;

    /** Platform admin ID — null for regular tenant users. */
    @Column(name = "admin_id", updatable = false)
    private Long adminId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    /** Null = active; non-null = revoked. */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoke_reason", length = 64)
    private String revokeReason;

    protected StoredRefreshToken() {}

    public StoredRefreshToken(Long id, String tokenId, Long identityId, Long adminId,
                              OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.tokenId = tokenId;
        this.identityId = identityId;
        this.adminId = adminId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return revokedAt == null && OffsetDateTime.now().isBefore(expiresAt);
    }

    public void revoke(String reason) {
        this.revokedAt = OffsetDateTime.now();
        this.revokeReason = reason;
    }

    // ========== Getters ==========

    public Long getId()           { return id; }
    public String getTokenId()    { return tokenId; }
    public Long getIdentityId()   { return identityId; }
    public Long getAdminId()      { return adminId; }
    public OffsetDateTime getIssuedAt()   { return issuedAt; }
    public OffsetDateTime getExpiresAt()  { return expiresAt; }
    public OffsetDateTime getRevokedAt()  { return revokedAt; }
    public String getRevokeReason()       { return revokeReason; }
}
