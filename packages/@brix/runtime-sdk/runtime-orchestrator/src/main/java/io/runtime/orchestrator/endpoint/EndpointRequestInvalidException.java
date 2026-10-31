/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.List;
import java.util.Map;

/**
 * A request breaks its endpoint's request schema (root BRX-RS-MANIFEST-022; standard-link plan L7.2). The runtime
 * answers 400 {@value #CODE} with the paths and rules the request breaks, never its values.
 */
public final class EndpointRequestInvalidException extends RuntimeException {

    /** The stable code. */
    public static final String CODE = "endpoint.invalid_request";

    private static final long serialVersionUID = 1L;

    private final transient List<Map<String, String>> violations;

    /**
     * Creates the exception.
     *
     * @param violations each broken rule, as {@code path} and {@code rule}
     */
    public EndpointRequestInvalidException(List<Map<String, String>> violations) {
        super(CODE);
        this.violations = List.copyOf(violations);
    }

    /**
     * Returns the broken rules.
     *
     * @return {@code path} and {@code rule} of each
     */
    public List<Map<String, String>> violations() {
        return violations;
    }
}
