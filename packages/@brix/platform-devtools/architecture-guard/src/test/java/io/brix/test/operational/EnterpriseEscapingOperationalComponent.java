/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.test.operational;

import io.brix.enterprise.ForbiddenEnterpriseService;

/**
 * Negative A-26 fixture with a forbidden enterprise implementation dependency.
 */
public final class EnterpriseEscapingOperationalComponent {

    private final ForbiddenEnterpriseService service;

    /**
     * Creates the violating fixture.
     *
     * @param service forbidden enterprise implementation
     */
    public EnterpriseEscapingOperationalComponent(ForbiddenEnterpriseService service) {
        this.service = service;
    }

    /**
     * Returns the forbidden dependency for bytecode import.
     *
     * @return service
     */
    public ForbiddenEnterpriseService service() {
        return service;
    }
}
