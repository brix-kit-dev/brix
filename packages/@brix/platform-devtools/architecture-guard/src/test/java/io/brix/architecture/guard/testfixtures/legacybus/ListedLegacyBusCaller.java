/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.testfixtures.legacybus;

import java.util.function.Supplier;

import io.runtime.sdk.context.RuntimeContext;

/** Takes the Host's bus from the runtime context, directly and in a nested class, and is listed. */
public class ListedLegacyBusCaller {

    Object bus(RuntimeContext context) {
        return context.getEventBus();
    }

    Supplier<Object> later(RuntimeContext context) {
        return new Supplier<>() {
            @Override
            public Object get() {
                return context.getEventBus();
            }
        };
    }
}
