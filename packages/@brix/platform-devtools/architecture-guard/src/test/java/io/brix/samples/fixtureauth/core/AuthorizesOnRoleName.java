/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixtureauth.core;

import io.runtime.sdk.capability.AuthCapability;

/** A plugin that lets a caller approve on a role name through the SDK: forbidden. */
public final class AuthorizesOnRoleName {

    private final AuthCapability auth;

    public AuthorizesOnRoleName(AuthCapability auth) {
        this.auth = auth;
    }

    public boolean mayApprove() {
        return auth.hasRole("ADMIN");
    }
}
