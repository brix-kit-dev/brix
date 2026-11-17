/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.operational;

import io.brix.platform.fixtureops.handler.FixtureHandler;
import io.brix.platform.fixtureowner.api.FixtureOwnerAction;
import io.brix.platform.fixtureowner.internal.FixtureOwnerAdministration;
import io.runtime.orchestrator.operational.OperationalContext;

/**
 * A compliant operational module: the operational SPI, an Owner's internal contract and api, and its own classes.
 */
public final class FixtureOperationalModule {

    private final OperationalContext context;

    /**
     * Creates the module.
     *
     * @param context the operational context
     */
    public FixtureOperationalModule(OperationalContext context) {
        this.context = context;
    }

    /**
     * Archives through the Owner's internal contract.
     *
     * @param id the record
     * @return the action taken
     */
    public FixtureOwnerAction archive(long id) {
        context.requireInternalContract(FixtureOwnerAdministration.class).archive(id);
        return new FixtureOwnerAction("fixture.archived");
    }

    /**
     * Returns the module's handlers: an array of its own class.
     *
     * @return the handlers
     */
    public FixtureHandler[] handlers() {
        return new FixtureHandler[] {new FixtureHandler(this)};
    }
}
