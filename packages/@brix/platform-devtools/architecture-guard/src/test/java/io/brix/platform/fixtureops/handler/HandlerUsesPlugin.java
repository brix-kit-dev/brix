/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.brix.samples.fixturebeta.core.BetaStock;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses a sample plugin's implementation.
 */
public final class HandlerUsesPlugin {

    private final BetaStock dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesPlugin(BetaStock dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public BetaStock dependency() {
        return dependency;
    }
}
