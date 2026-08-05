/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.sdk.event.identity;

import java.util.Objects;

/**
 * Runtime-local signal emitted after a FIRST_OWNER invitee completes identity
 * setup.
 *
 * <p>This contract carries only the identity id. The tenant Owner must resolve
 * the email and invitation state through governed contracts; setup tokens,
 * invitation tokens, URLs, and tenant authority are never carried here.</p>
 *
 * @param identityId activated invitee identity id
 * @since 3.2.0
 */
public record FirstOwnerInviteeSetupCompletedEvent(Long identityId) {

    public FirstOwnerInviteeSetupCompletedEvent {
        Objects.requireNonNull(identityId, "identityId must not be null");
    }
}
