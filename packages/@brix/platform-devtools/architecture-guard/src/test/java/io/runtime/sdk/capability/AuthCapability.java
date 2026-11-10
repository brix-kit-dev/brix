/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.runtime.sdk.capability;

/** Stand-in for the SDK's auth capability, under its real name: the guard does not depend on the SDK. */
public interface AuthCapability {

    boolean hasPermission(String permission);

    boolean hasRole(String role);
}
