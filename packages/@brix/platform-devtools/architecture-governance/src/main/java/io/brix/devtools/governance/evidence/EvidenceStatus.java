/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

/**
 * Machine status for one applicable architecture requirement.
 */
public enum EvidenceStatus {
    ENFORCED_PASSING("enforced-passing"),
    MISSING_BLOCKING("missing-blocking"),
    PARTIAL("partial"),
    FAILING("failing"),
    WAIVED_UNTIL("waived-until"),
    NOT_APPLICABLE("not-applicable");

    private final String wireValue;

    EvidenceStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the stable registry/report value.
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns true when no waiver is needed.
     */
    public boolean isCleanPass() {
        return this == ENFORCED_PASSING || this == NOT_APPLICABLE;
    }
}
