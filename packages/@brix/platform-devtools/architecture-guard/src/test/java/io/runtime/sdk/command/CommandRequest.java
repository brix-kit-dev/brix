/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.command;

/** Stand-in for the SDK's string command request, under its real name: the guard does not depend on the SDK. */
public record CommandRequest<C>(String commandType, C payload, String idempotencyScope, String idempotencyKey) {

    public static <C> CommandRequest<C> of(String commandType, C payload, String idempotencyScope,
            String idempotencyKey) {
        return new CommandRequest<>(commandType, payload, idempotencyScope, idempotencyKey);
    }
}
