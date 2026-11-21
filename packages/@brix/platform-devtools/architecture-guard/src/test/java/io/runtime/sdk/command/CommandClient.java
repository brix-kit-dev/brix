/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.command;

/** Stand-in for the SDK's typed command client, under its real name: the guard does not depend on the SDK. */
public interface CommandClient<C> {

    Object submit(C command, String idempotencyKey);
}
