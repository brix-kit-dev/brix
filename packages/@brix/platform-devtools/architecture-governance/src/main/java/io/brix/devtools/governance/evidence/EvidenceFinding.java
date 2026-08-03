/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

/**
 * Stable diagnostic emitted by the Phase 6 evidence gate.
 */
public record EvidenceFinding(
    FindingSeverity severity,
    String code,
    String requirementId,
    String artifact,
    String message,
    String evidenceUri
) {

    public EvidenceFinding {
        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }
        requireText(code, "code");
        requireText(requirementId, "requirementId");
        requireText(artifact, "artifact");
        requireText(message, "message");
        evidenceUri = evidenceUri == null ? "" : evidenceUri;
    }

    /**
     * Returns true when this finding must block the selected gate.
     */
    public boolean isBlocking() {
        return severity == FindingSeverity.BLOCKING;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
