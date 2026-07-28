/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.event;

import java.util.Map;
import java.util.Objects;

import io.runtime.sdk.event.IntegrationEvent;

/**
 * Critical integration fact emitted after the first tenant owner accepts an invitation.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class TenantFirstOwnerAcceptedEvent extends IntegrationEvent {

    public static final String EVENT_TYPE = "TenantFirstOwnerAccepted";
    public static final String SOURCE_MODULE = "platform-tenant";

    private final Long tenantId;
    private final Long memberId;
    private final Long profileId;
    private final Long invitationId;

    /**
     * Creates the accepted-owner fact payload.
     *
     * @param tenantId tenant id
     * @param memberId created owner member id
     * @param profileId created profile id
     * @param invitationId accepted invitation id
     */
    public TenantFirstOwnerAcceptedEvent(Long tenantId, Long memberId, Long profileId, Long invitationId) {
        super(SOURCE_MODULE, 1, String.valueOf(Objects.requireNonNull(tenantId, "tenantId must not be null")));
        this.tenantId = tenantId;
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.profileId = Objects.requireNonNull(profileId, "profileId must not be null");
        this.invitationId = Objects.requireNonNull(invitationId, "invitationId must not be null");
    }

    @Override
    public String getRoutingKey() {
        return String.valueOf(tenantId);
    }

    /**
     * Returns the stable manifest event id.
     *
     * @return event contract id
     */
    public String eventTypeId() {
        return EVENT_TYPE;
    }

    /**
     * Returns the approved canonical payload fields.
     *
     * @return payload map
     */
    public Map<String, Object> payload() {
        return Map.of(
            "tenantId", tenantId,
            "memberId", memberId,
            "profileId", profileId,
            "invitationId", invitationId);
    }

    public Long tenantIdValue() {
        return tenantId;
    }

    public Long memberId() {
        return memberId;
    }

    public Long profileId() {
        return profileId;
    }

    public Long invitationId() {
        return invitationId;
    }
}
