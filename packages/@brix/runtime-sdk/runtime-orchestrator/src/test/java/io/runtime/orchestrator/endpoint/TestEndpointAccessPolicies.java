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
 * Builds endpoint access policies through the same declaration parser the
 * Runtime uses for manifests and operational descriptors.
 */
public final class TestEndpointAccessPolicies {

    private TestEndpointAccessPolicies() {
    }

    /**
     * Authenticated platform endpoint that requires every given permission.
     *
     * @param permissions required permission IDs
     * @return policy
     */
    public static EndpointAccessPolicy platform(String... permissions) {
        return EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "platform-token"),
            Map.of("permissions", List.of(permissions), "decision", "all"),
            Map.of("mode", "forbidden"));
    }

    /**
     * Authenticated endpoint without a token-class restriction.
     *
     * @param permissions required permission IDs
     * @return policy
     */
    public static EndpointAccessPolicy authenticated(String... permissions) {
        return EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required"),
            Map.of("permissions", List.of(permissions)),
            Map.of("mode", "optional"));
    }

    /**
     * Approved anonymous endpoint with a named, non-permission policy.
     *
     * @param policyRef named authorization policy reference
     * @return policy
     */
    public static EndpointAccessPolicy anonymous(String policyRef) {
        return EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "anonymous"),
            Map.of("policyRef", policyRef),
            Map.of("mode", "forbidden"));
    }
}
