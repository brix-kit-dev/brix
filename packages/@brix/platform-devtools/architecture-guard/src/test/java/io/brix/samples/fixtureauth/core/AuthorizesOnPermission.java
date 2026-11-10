/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.samples.fixtureauth.core;

import io.runtime.sdk.capability.AuthCapability;

/** A plugin that lets a caller approve on a permission code through the SDK: allowed. */
public final class AuthorizesOnPermission {

    private final AuthCapability auth;

    public AuthorizesOnPermission(AuthCapability auth) {
        this.auth = auth;
    }

    public boolean mayApprove() {
        return auth.hasPermission("fixture:order:approve");
    }
}
