/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.runtime.sdk.capability.registry.CapabilityRegistry;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses the general capability registry.
 */
public final class HandlerUsesRegistry {

    private final CapabilityRegistry dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesRegistry(CapabilityRegistry dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public CapabilityRegistry dependency() {
        return dependency;
    }
}
