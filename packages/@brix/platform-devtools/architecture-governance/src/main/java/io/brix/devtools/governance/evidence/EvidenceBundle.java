/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.util.List;

/**
 * Complete Phase 6 input set for one CI or release gate evaluation.
 */
public record EvidenceBundle(
    String baselineId,
    String commitId,
    List<ArchitectureEvidence> evidence,
    List<ConsumerCoverage> coverage,
    List<SupplyChainEvidence> supplyChain,
    List<ArchitectureWaiver> waivers
) {

    public EvidenceBundle {
        requireText(baselineId, "baselineId");
        requireText(commitId, "commitId");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        coverage = List.copyOf(coverage == null ? List.of() : coverage);
        supplyChain = List.copyOf(supplyChain == null ? List.of() : supplyChain);
        waivers = List.copyOf(waivers == null ? List.of() : waivers);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
