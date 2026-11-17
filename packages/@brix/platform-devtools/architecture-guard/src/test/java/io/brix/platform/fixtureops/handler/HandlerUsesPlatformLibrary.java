/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.brix.platform.fixture.CompliantPlatformService;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses a platform library class.
 */
public final class HandlerUsesPlatformLibrary {

    private final CompliantPlatformService dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesPlatformLibrary(CompliantPlatformService dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public CompliantPlatformService dependency() {
        return dependency;
    }
}
