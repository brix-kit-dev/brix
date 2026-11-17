/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability.registry;

/**
 * Stand-in for the general capability registry, which an operational module must not obtain (root A-26).
 */
public interface CapabilityRegistry {

    /**
     * Looks a capability up.
     *
     * @param type the capability type
     * @param <T> the capability type
     * @return the capability
     */
    <T> T get(Class<T> type);
}
