/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

/**
 * A handler did not finish before its endpoint's deadline, or answered only after it (root BRX-RS-MANIFEST-022,
 * DATA-011; standard-link plan L7.5, queue item 6). The runtime has set the invocation's cancellation signal, rolls the
 * unit of work back and answers 504 {@value #CODE}.
 */
public final class EndpointDeadlineExceededException extends RuntimeException {

    /** The stable code. */
    public static final String CODE = "endpoint.deadline_exceeded";

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param endpointId the endpoint
     */
    public EndpointDeadlineExceededException(String endpointId) {
        super(CODE + ": " + endpointId, null, false, false);
    }
}
