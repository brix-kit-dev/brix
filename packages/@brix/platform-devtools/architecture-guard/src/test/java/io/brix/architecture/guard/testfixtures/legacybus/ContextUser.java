/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.testfixtures.legacybus;

import io.runtime.sdk.context.RuntimeContext;

/** Holds the runtime context but never takes the bus from it. */
public class ContextUser {

    private final RuntimeContext context;

    public ContextUser(RuntimeContext context) {
        this.context = context;
    }

    RuntimeContext context() {
        return context;
    }
}
