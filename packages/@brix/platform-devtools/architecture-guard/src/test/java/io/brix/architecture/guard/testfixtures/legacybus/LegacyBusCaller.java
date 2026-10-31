/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.testfixtures.legacybus;

import io.runtime.sdk.context.RuntimeContext;

/** Takes the Host's bus from the runtime context and is not listed. */
public class LegacyBusCaller {

    Object bus(RuntimeContext context) {
        return context.getEventBus();
    }
}
