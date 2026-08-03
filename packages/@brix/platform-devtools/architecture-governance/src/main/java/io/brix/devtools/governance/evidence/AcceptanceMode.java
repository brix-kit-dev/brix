/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

/**
 * Defines how strict the Phase 6 evidence gate must be for a CI or release
 * decision.
 */
public enum AcceptanceMode {
    /**
     * CI mode allows valid time-bound waivers to keep migration work auditable.
     */
    CI_GATE,

    /**
     * Release mode allows only low-priority valid waivers and blocks P0/P1.
     */
    RELEASE_ACCEPTANCE,

    /**
     * Implementation Accepted mode requires every applicable item to pass.
     */
    IMPLEMENTATION_ACCEPTED
}
