/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.fixtureops.handler;

import io.brix.enterprise.ForbiddenEnterpriseService;

/**
 * Negative fixture: an operational handler, in a package not named operational, that uses an enterprise implementation.
 */
public final class HandlerUsesEnterprise {

    private final ForbiddenEnterpriseService dependency;

    /**
     * Creates the violating fixture.
     *
     * @param dependency the forbidden dependency
     */
    public HandlerUsesEnterprise(ForbiddenEnterpriseService dependency) {
        this.dependency = dependency;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return the dependency
     */
    public ForbiddenEnterpriseService dependency() {
        return dependency;
    }
}
