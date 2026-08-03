/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

/**
 * Stable Phase 1 verifier failure used by build-time governance tests.
 */
public final class ArchitectureGovernanceException extends RuntimeException {

    public ArchitectureGovernanceException(String message) {
        super(message);
    }
}
