/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.ticket;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Persistent one-time context selection ticket.
 *
 * <p>The client receives only the opaque ticket value. The database stores a
 * SHA-256 hash, bound identity ID, identity-token jti, target context, and
 * expiry/consumption metadata.</p>
 *
 * @since 3.2.2
 */
@Entity
@Table(
        name = "auth_context_selection_ticket",
        indexes = {
                @Index(name = "idx_auth_context_ticket_identity", columnList = "identity_id"),
                @Index(name = "idx_auth_context_ticket_expires", columnList = "expires_at")
        }
)
public class ContextSelectionTicket {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "ticket_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String ticketHash;

    @Column(name = "identity_id", nullable = false, updatable = false)
    private Long identityId;

    @Column(name = "identity_token_jti", nullable = false, length = 64, updatable = false)
    private String identityTokenJti;

    @Column(name = "role_type", nullable = false, length = 16, updatable = false)
    private String roleType;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "ref_id", nullable = false, updatable = false)
    private Long refId;

    @Column(name = "context_id", nullable = false, length = 36, updatable = false)
    private String contextId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    protected ContextSelectionTicket() {
    }

    public ContextSelectionTicket(Long id, String ticketHash, Long identityId, String identityTokenJti,
                                  String roleType, Long tenantId, Long refId, String contextId,
                                  OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.ticketHash = ticketHash;
        this.identityId = identityId;
        this.identityTokenJti = identityTokenJti;
        this.roleType = roleType;
        this.tenantId = tenantId;
        this.refId = refId;
        this.contextId = contextId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(OffsetDateTime now) {
        return consumedAt == null && now != null && now.isBefore(expiresAt);
    }

    public void consume(OffsetDateTime now) {
        this.consumedAt = now;
    }

    public Long getId() { return id; }
    public String getTicketHash() { return ticketHash; }
    public Long getIdentityId() { return identityId; }
    public String getIdentityTokenJti() { return identityTokenJti; }
    public String getRoleType() { return roleType; }
    public Long getTenantId() { return tenantId; }
    public Long getRefId() { return refId; }
    public String getContextId() { return contextId; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
}
