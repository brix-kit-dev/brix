/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * External attestation set for one released Host, plugin, adapter, or remote.
 */
public record SupplyChainEvidence(
    String artifact,
    boolean lockedDependencies,
    String lockEvidenceUri,
    String sbomUri,
    String licenseReportUri,
    String scaReportUri,
    String digest,
    String signatureUri,
    String provenanceUri,
    boolean trustedCi,
    Instant producedAt,
    Duration validFor
) {

    public SupplyChainEvidence {
        requireText(artifact, "artifact");
        lockEvidenceUri = normalize(lockEvidenceUri);
        sbomUri = normalize(sbomUri);
        licenseReportUri = normalize(licenseReportUri);
        scaReportUri = normalize(scaReportUri);
        digest = normalize(digest);
        signatureUri = normalize(signatureUri);
        provenanceUri = normalize(provenanceUri);
        if (producedAt == null) {
            throw new IllegalArgumentException("producedAt is required");
        }
        if (validFor == null || validFor.isNegative() || validFor.isZero()) {
            throw new IllegalArgumentException("validFor must be positive");
        }
    }

    /**
     * Returns every required supply-chain proof that is absent or invalid.
     */
    public List<String> missingProofs() {
        List<String> missing = new ArrayList<>();
        if (!lockedDependencies) {
            missing.add("locked-dependencies");
        }
        if (lockEvidenceUri.isBlank()) {
            missing.add("lock");
        }
        if (sbomUri.isBlank()) {
            missing.add("sbom");
        }
        if (licenseReportUri.isBlank()) {
            missing.add("license");
        }
        if (scaReportUri.isBlank()) {
            missing.add("sca");
        }
        if (!digest.startsWith("sha256:")) {
            missing.add("digest");
        }
        if (signatureUri.isBlank()) {
            missing.add("signature");
        }
        if (provenanceUri.isBlank()) {
            missing.add("provenance");
        }
        if (!trustedCi) {
            missing.add("trusted-ci");
        }
        return missing;
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
