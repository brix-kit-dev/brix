/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Evidence emitted by one executor for one requirement and artifact.
 */
public record ArchitectureEvidence(
    String requirementId,
    String artifact,
    String executor,
    EvidenceStatus status,
    int selectedTargetCount,
    String evidenceUri,
    Instant producedAt,
    Duration validFor,
    List<String> diagnostics
) {

    public ArchitectureEvidence {
        requireText(requirementId, "requirementId");
        requireText(artifact, "artifact");
        requireText(executor, "executor");
        requireText(evidenceUri, "evidenceUri");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (selectedTargetCount < 0) {
            throw new IllegalArgumentException("selectedTargetCount must not be negative");
        }
        if (producedAt == null) {
            throw new IllegalArgumentException("producedAt is required");
        }
        if (validFor == null || validFor.isNegative() || validFor.isZero()) {
            throw new IllegalArgumentException("validFor must be positive");
        }
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    /**
     * Returns the evidence expiry instant.
     */
    public Instant expiresAt() {
        return producedAt.plus(validFor);
    }

    /**
     * Returns whether this evidence is expired at the supplied reference time.
     */
    public boolean isExpired(Instant now) {
        return !expiresAt().isAfter(now);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
