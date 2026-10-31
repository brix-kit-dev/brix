/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.util.Objects;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Who calls a query: the calling plugin and, when that plugin is handling a user's request, the user
 * (root BRX-RS-MANIFEST-028, ERROR-006).
 *
 * <p>The runtime fills it in from the call it admitted; a plugin cannot name itself or a user. Calls made while
 * handling an event, a command or a task have no user.</p>
 *
 * @param pluginId the calling plugin
 * @param principalId the verified user the caller acts for, if any
 * @param principalRole that user's token role ({@code actor}, {@code subject} or {@code platform}), if any
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public record CallerIdentity(String pluginId, Optional<String> principalId, Optional<String> principalRole) {

    /**
     * Validates the identity.
     */
    public CallerIdentity {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        principalId = Objects.requireNonNull(principalId, "principalId must not be null");
        principalRole = Objects.requireNonNull(principalRole, "principalRole must not be null");
        if (principalId.isEmpty() && principalRole.isPresent()) {
            throw new IllegalArgumentException("a principal role needs a principal");
        }
    }

    /**
     * Returns an identity without a user.
     *
     * @param pluginId the calling plugin
     * @return the identity
     */
    public static CallerIdentity service(String pluginId) {
        return new CallerIdentity(pluginId, Optional.empty(), Optional.empty());
    }

    /**
     * Returns whether the caller acts for a user.
     *
     * @return {@code true} when a verified user is present
     */
    public boolean hasPrincipal() {
        return principalId.isPresent();
    }
}
