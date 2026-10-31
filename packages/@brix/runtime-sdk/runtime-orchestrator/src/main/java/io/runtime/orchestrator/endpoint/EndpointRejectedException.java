/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

/**
 * An endpoint already runs as many calls as the host allows it (root BRX-RS-RESILIENCE-001; standard-link plan L7.5,
 * D60): the call is answered 503 {@value #CODE} at once, before its handler runs.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class EndpointRejectedException extends RuntimeException {

    /** The wire code of the failure. */
    public static final String CODE = "endpoint.rejected";

    private static final long serialVersionUID = 1L;

    /**
     * Creates the failure.
     *
     * @param endpointId the endpoint that is full
     */
    public EndpointRejectedException(String endpointId) {
        super(CODE + ": " + endpointId, null, false, false);
    }
}
