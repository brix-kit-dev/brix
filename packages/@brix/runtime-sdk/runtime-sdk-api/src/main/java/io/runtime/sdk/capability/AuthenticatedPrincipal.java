/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.util.Set;

/**
 * Authenticated principal details exposed by the Runtime Auth capability.
 *
 * <p>The interface carries stable, implementation-neutral authentication
 * attributes. Runtime endpoint adapters may use it to build trusted invocation
 * context without exposing bearer tokens, JWT claims, servlet security objects,
 * or platform-auth implementation classes to plugin handlers.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface AuthenticatedPrincipal extends TenantAwarePrincipal {

    /**
     * Returns the identity email verified by the authentication provider.
     *
     * @return authenticated identity email, or {@code null} when not available
     */
    String getEmail();

    /**
     * Returns the authenticated token role such as {@code actor},
     * {@code subject}, {@code platform-admin}, or {@code bootstrap}.
     *
     * @return token role value, or {@code null} when not available
     */
    String getTokenRole();

    /**
     * Returns the authenticated token type such as {@code access}.
     *
     * @return token type value, or {@code null} when not available
     */
    String getTokenType();

    /**
     * Returns restricted actions allowed by the authenticated token.
     *
     * @return allowed actions; empty when no restricted action is present
     */
    default Set<String> getAllowedActions() {
        return Set.of();
    }
}
