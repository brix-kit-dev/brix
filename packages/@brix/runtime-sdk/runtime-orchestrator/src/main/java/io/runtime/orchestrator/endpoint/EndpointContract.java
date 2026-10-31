/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.Set;

/**
 * What an endpoint's manifest says about its request and its response (root BRX-RS-MANIFEST-022; standard-link plan
 * L7.2, L7.3).
 *
 * @param requestSchemaRef the request's {@code brix://schemas/...} reference, blank when none is declared
 * @param responseSchemaRef the response's {@code brix://schemas/...} reference, blank when none is declared
 * @param status the status a success answers with: 200, 201, 202 or 204
 */
public record EndpointContract(String requestSchemaRef, String responseSchemaRef, int status) {

    private static final Set<Integer> STATUSES = Set.of(200, 201, 202, 204);

    /**
     * Checks the contract.
     */
    public EndpointContract {
        requestSchemaRef = requestSchemaRef == null ? "" : requestSchemaRef.trim();
        responseSchemaRef = responseSchemaRef == null ? "" : responseSchemaRef.trim();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("a successful endpoint answers 200, 201, 202 or 204, not " + status);
        }
    }

    /**
     * Returns the contract of an endpoint that declares no schemas, answering 200.
     *
     * @return the contract
     */
    public static EndpointContract none() {
        return new EndpointContract("", "", 200);
    }
}
