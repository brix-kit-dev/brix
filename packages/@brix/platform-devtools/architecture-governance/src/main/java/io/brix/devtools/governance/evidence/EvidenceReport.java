/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.time.Instant;
import java.util.List;

/**
 * Deterministic result of a Phase 6 evidence gate evaluation.
 */
public record EvidenceReport(
    String baselineId,
    String commitId,
    AcceptanceMode mode,
    Instant generatedAt,
    List<EvidenceFinding> findings,
    List<ArchitectureEvidence> evidence,
    List<SupplyChainEvidence> supplyChain
) {

    public EvidenceReport {
        requireText(baselineId, "baselineId");
        requireText(commitId, "commitId");
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt is required");
        }
        findings = List.copyOf(findings == null ? List.of() : findings);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        supplyChain = List.copyOf(supplyChain == null ? List.of() : supplyChain);
    }

    /**
     * Returns true only when the selected gate has no blocking finding.
     */
    public boolean accepted() {
        return findings.stream().noneMatch(EvidenceFinding::isBlocking);
    }

    /**
     * Returns the number of blocking findings.
     */
    public long blockingFindingCount() {
        return findings.stream().filter(EvidenceFinding::isBlocking).count();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
