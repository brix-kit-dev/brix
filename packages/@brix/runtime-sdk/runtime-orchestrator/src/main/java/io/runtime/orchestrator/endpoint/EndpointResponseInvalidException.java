/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

/**
 * A handler's response breaks its endpoint's response schema (root BRX-RS-MANIFEST-022; standard-link plan L7.3).
 * The runtime logs the broken rules, rolls the unit of work back and answers 500 {@code platform.unknown}: the
 * caller cannot fix it.
 */
public final class EndpointResponseInvalidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param endpointId the endpoint
     */
    public EndpointResponseInvalidException(String endpointId) {
        super("The response of " + endpointId + " breaks its schema", null, false, false);
    }
}
