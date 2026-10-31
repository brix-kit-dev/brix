/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.util.regex.Pattern;

/**
 * A service a module declares under {@code externalServices} (root BRX-RS-ERROR-014; standard-link plan L8.2).
 *
 * @param id the id the module's code calls it by
 * @param baseUrlRef the host directory entry that gives its address
 * @param authPolicyRef how the runtime authenticates the calls; {@value ExternalHttpPolicies#AUTH_NONE}
 * @param timeoutPolicyRef the timeout policy
 * @param retryPolicyRef the retry policy
 * @param concurrencyPolicyRef the concurrency policy
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record ExternalServiceDeclaration(String id, String baseUrlRef, String authPolicyRef, String timeoutPolicyRef,
        String retryPolicyRef, String concurrencyPolicyRef) {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");

    /**
     * Checks the declaration.
     *
     * @throws IllegalArgumentException when an id is malformed or the runtime does not know a policy
     */
    public ExternalServiceDeclaration {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("an external service id is lower-case letters, digits and -: " + id);
        }
        if (baseUrlRef == null || !ID.matcher(baseUrlRef).matches()) {
            throw new IllegalArgumentException("external service '" + id
                + "' needs a baseUrlRef of lower-case letters, digits and -: " + baseUrlRef);
        }
        authPolicyRef = authPolicyRef == null || authPolicyRef.isBlank() ? ExternalHttpPolicies.AUTH_NONE
            : authPolicyRef.trim();
        ExternalHttpPolicies.check(authPolicyRef, timeoutPolicyRef, retryPolicyRef, concurrencyPolicyRef);
    }
}
