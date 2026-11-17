/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.brix.platform.fixtureops.operational.FixtureOperationalModule;

/**
 * A compliant handler of the operational module, in a package not named operational.
 */
public final class FixtureHandler {

    private final FixtureOperationalModule module;

    /**
     * Creates the handler.
     *
     * @param module its module
     */
    public FixtureHandler(FixtureOperationalModule module) {
        this.module = module;
    }

    /**
     * Archives a record.
     *
     * @param id the record
     * @return the action's ID
     */
    public String handle(long id) {
        return module.archive(id).id();
    }
}
