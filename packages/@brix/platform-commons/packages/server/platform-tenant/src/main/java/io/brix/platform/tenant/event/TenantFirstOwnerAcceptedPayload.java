/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.event;

import java.util.Objects;

/**
 * Payload of {@code TenantFirstOwnerAccepted} 1.x, as {@link TenantFirstOwnerAcceptedEvent#payload()}
 * writes it to the outbox.
 *
 * <p>The runtime decodes the outbox row into this record before it calls the subscriber; a row
 * without one of the fields does not decode and is parked.</p>
 *
 * @param tenantId tenant whose first owner accepted
 * @param memberId created owner member id
 * @param profileId created profile id
 * @param invitationId accepted invitation id
 */
public record TenantFirstOwnerAcceptedPayload(Long tenantId, Long memberId, Long profileId, Long invitationId) {

    public TenantFirstOwnerAcceptedPayload {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(profileId, "profileId must not be null");
        Objects.requireNonNull(invitationId, "invitationId must not be null");
    }
}
