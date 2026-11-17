/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses the Runtime outside the operational SPI.
 */
public final class HandlerUsesRuntime {

    private final RuntimeShellFatalAction dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesRuntime(RuntimeShellFatalAction dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public RuntimeShellFatalAction dependency() {
        return dependency;
    }
}
