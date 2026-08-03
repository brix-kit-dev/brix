/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.endpoint;

/** Request to accept a FIRST_OWNER invitation with the authenticated identity. */
public record AcceptFirstOwnerInvitationRequest(String invitationToken) {
    public AcceptFirstOwnerInvitationRequest {
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new IllegalArgumentException("invitationToken is required");
        }
    }
}
