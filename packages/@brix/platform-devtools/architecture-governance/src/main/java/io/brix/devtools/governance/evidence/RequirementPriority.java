/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

/**
 * Requirement priority used by release waiver policy.
 */
public enum RequirementPriority {
    P0,
    P1,
    P2,
    P3;

    /**
     * Returns true when a waiver at this priority cannot pass release gates.
     */
    public boolean blocksGaOrRelease() {
        return this == P0 || this == P1;
    }
}
