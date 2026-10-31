/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.context;

/** Stand-in for the SDK's runtime context, under its real name: the guard does not depend on the SDK. */
public interface RuntimeContext {

    Object getEventBus();
}
