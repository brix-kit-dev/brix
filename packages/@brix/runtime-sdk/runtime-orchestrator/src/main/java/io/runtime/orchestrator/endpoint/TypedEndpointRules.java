/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.Optional;

import io.runtime.orchestrator.event.EventPolicies;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * What an endpoint needs in order to start, the same for a plugin's and an operational module's (root
 * BRX-RS-HOST-014; idempotency design D78).
 *
 * <ul>
 *   <li>A typed handler has its schemas on the class path: the runtime checks every request and response against them
 *   (standard-link plan L7.2).</li>
 *   <li>An endpoint whose results are replayed has a typed handler, and its response holds no secret or credential:
 *   the runtime keeps the answer (idempotency design §4.6, D74).</li>
 * </ul>
 */
public final class TypedEndpointRules {

    private TypedEndpointRules() {
    }

    /**
     * Finds what keeps an endpoint from starting.
     *
     * @param handler its handler
     * @param contract its contract
     * @param classes the class path of its schemas
     * @return what is wrong, as the rest of a sentence that begins with the endpoint's name
     */
    public static Optional<String> problem(Object handler, EndpointContract contract, ClassLoader classes) {
        boolean replayed = contract.replay() != null;
        if (!(handler instanceof TypedEndpointHandler<?, ?>)) {
            return replayed
                ? Optional.of("has its results replayed, so its handler must be a TypedEndpointHandler")
                : Optional.empty();
        }
        Optional<String> request = missing("request", contract.requestSchemaRef(), classes);
        if (request.isPresent() || contract.status() == 204) {
            return request;
        }
        Optional<String> response = missing("response", contract.responseSchemaRef(), classes);
        if (response.isPresent() || !replayed) {
            return response;
        }
        return ReplayableAnswers.fieldNotKept(contract.responseSchemaRef(), classes)
            .map(field -> "has its results replayed, and its response declares a secret or credential at " + field
                + "; the runtime does not keep those");
    }

    private static Optional<String> missing(String kind, String schemaRef, ClassLoader classes) {
        String resource = EventPolicies.schemaResource(schemaRef);
        return resource != null && classes.getResource(resource) != null
            ? Optional.empty()
            : Optional.of("has a typed handler, so its " + kind + " schema '" + schemaRef
                + "' must be on its class path");
    }
}
