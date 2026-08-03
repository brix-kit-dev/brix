/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.time.Instant;

/**
 * Time-bound governance waiver for one requirement and artifact.
 */
public record ArchitectureWaiver(
    String requirementId,
    String artifact,
    String owner,
    String reason,
    String risk,
    String compensatingControl,
    Instant expiresAt,
    String approver,
    RequirementPriority priority
) {

    public ArchitectureWaiver {
        requireText(requirementId, "requirementId");
        requireText(artifact, "artifact");
        requireText(owner, "owner");
        requireText(reason, "reason");
        requireText(risk, "risk");
        requireText(compensatingControl, "compensatingControl");
        requireText(approver, "approver");
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority is required");
        }
    }

    /**
     * Returns whether this waiver is still valid at the supplied reference time.
     */
    public boolean isActiveAt(Instant now) {
        return expiresAt.isAfter(now);
    }

    /**
     * Returns whether this waiver covers a requirement and artifact pair.
     */
    public boolean covers(String candidateRequirementId, String candidateArtifact) {
        return requirementId.equals(candidateRequirementId)
            && (artifact.equals(candidateArtifact) || artifact.equals("*"));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
