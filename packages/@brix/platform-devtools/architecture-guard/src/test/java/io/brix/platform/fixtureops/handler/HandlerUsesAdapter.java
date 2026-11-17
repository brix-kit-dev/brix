/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.infra.adapter.persistence.jpa.JdbcAdapter;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses an L2C adapter's implementation.
 */
public final class HandlerUsesAdapter {

    private final JdbcAdapter dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesAdapter(JdbcAdapter dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public JdbcAdapter dependency() {
        return dependency;
    }
}
