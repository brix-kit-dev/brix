/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.Set;

/**
 * What an endpoint's manifest says about its request, its response and its deadline (root BRX-RS-MANIFEST-018,
 * MANIFEST-022; standard-link plan L7.2, L7.3, L7.5).
 *
 * @param requestSchemaRef the request's {@code brix://schemas/...} reference, blank when none is declared
 * @param responseSchemaRef the response's {@code brix://schemas/...} reference, blank when none is declared
 * @param status the status a success answers with: 200, 201, 202 or 204
 * @param timeoutPolicyRef the deadline policy of {@link EndpointPolicies}; blank means
 *     {@value EndpointPolicies#TIMEOUT_DEFAULT}
 */
public record EndpointContract(String requestSchemaRef, String responseSchemaRef, int status,
        String timeoutPolicyRef) {

    private static final Set<Integer> STATUSES = Set.of(200, 201, 202, 204);

    /**
     * Checks the contract.
     *
     * @throws IllegalArgumentException when the status is not a success status or the runtime does not know the
     *     timeout policy
     */
    public EndpointContract {
        requestSchemaRef = requestSchemaRef == null ? "" : requestSchemaRef.trim();
        responseSchemaRef = responseSchemaRef == null ? "" : responseSchemaRef.trim();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("a successful endpoint answers 200, 201, 202 or 204, not " + status);
        }
        timeoutPolicyRef = EndpointPolicies.timeout(timeoutPolicyRef);
    }

    /**
     * Creates a contract with the host's endpoint deadline.
     *
     * @param requestSchemaRef the request's schema reference, blank when none is declared
     * @param responseSchemaRef the response's schema reference, blank when none is declared
     * @param status the status a success answers with
     */
    public EndpointContract(String requestSchemaRef, String responseSchemaRef, int status) {
        this(requestSchemaRef, responseSchemaRef, status, EndpointPolicies.TIMEOUT_DEFAULT);
    }

    /**
     * Returns the contract of an endpoint that declares no schemas, answering 200 within the host's endpoint deadline.
     *
     * @return the contract
     */
    public static EndpointContract none() {
        return new EndpointContract("", "", 200);
    }

    /**
     * Returns the contract of an endpoint that declares only its deadline.
     *
     * @param timeoutPolicyRef the declared timeout policy, blank for the host's endpoint deadline
     * @return the contract
     */
    public static EndpointContract timeout(String timeoutPolicyRef) {
        return new EndpointContract("", "", 200, timeoutPolicyRef);
    }
}
