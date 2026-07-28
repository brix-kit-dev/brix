/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.brix.platform.tenant.internal.FirstOwnerInvitationView;

/**
 * Platform-safe FIRST_OWNER invitation view.
 *
 * @param invitationId invitation identifier serialized as a string
 * @param tenantId tenant identifier serialized as a string
 * @param inviteeEmail invitee email address
 * @param status invitation lifecycle status
 * @param expiresAt expiry timestamp
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record FirstOwnerInvitationDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long invitationId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long tenantId,
        String inviteeEmail,
        String status,
        OffsetDateTime expiresAt) {

    static FirstOwnerInvitationDto from(FirstOwnerInvitationView view) {
        return new FirstOwnerInvitationDto(
            view.id(),
            view.tenantId(),
            view.inviteeEmail(),
            view.status(),
            view.expiresAt());
    }
}
