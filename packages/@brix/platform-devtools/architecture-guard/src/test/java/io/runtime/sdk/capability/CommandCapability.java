/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.command.CommandRequest;
import io.runtime.sdk.command.CommandType;

/** Stand-in for the SDK's command capability, under its real name: the guard does not depend on the SDK. */
public interface CommandCapability {

    <C> CommandClient<C> client(CommandType<C> type);

    <C> Object submit(CommandRequest<C> request);
}
