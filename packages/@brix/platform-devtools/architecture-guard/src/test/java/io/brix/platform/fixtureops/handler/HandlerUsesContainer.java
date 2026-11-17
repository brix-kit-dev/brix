/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import org.springframework.context.ApplicationContext;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses the Spring container.
 */
public final class HandlerUsesContainer {

    private final ApplicationContext dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesContainer(ApplicationContext dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public ApplicationContext dependency() {
        return dependency;
    }
}
