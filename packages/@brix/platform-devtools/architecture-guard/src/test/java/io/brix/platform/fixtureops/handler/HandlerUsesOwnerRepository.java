/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.brix.platform.fixtureowner.repository.FixtureOwnerRepository;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses a Data Owner's repository.
 */
public final class HandlerUsesOwnerRepository {

    private final FixtureOwnerRepository dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesOwnerRepository(FixtureOwnerRepository dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public FixtureOwnerRepository dependency() {
        return dependency;
    }
}
