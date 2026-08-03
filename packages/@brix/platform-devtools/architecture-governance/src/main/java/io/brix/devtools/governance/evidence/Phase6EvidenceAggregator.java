/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregates Phase 6 executor evidence, consumer coverage, supply-chain
 * attestations, and waivers into one fail-closed gate decision.
 */
public final class Phase6EvidenceAggregator {

    /**
     * Evaluates a bundle at the supplied reference time.
     */
    public EvidenceReport aggregate(EvidenceBundle bundle, AcceptanceMode mode, Instant now) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }

        List<EvidenceFinding> findings = new ArrayList<>();
        evaluateCoverage(bundle.coverage(), findings);
        evaluateEvidence(bundle.evidence(), bundle.waivers(), mode, now, findings);
        evaluateWaivers(bundle.waivers(), mode, now, findings);
        evaluateSupplyChain(bundle, mode, now, findings);

        return new EvidenceReport(
            bundle.baselineId(),
            bundle.commitId(),
            mode,
            now,
            findings,
            bundle.evidence(),
            bundle.supplyChain());
    }

    private void evaluateCoverage(List<ConsumerCoverage> coverage, List<EvidenceFinding> findings) {
        if (coverage.isEmpty()) {
            findings.add(blocking("BRX-P6-COVERAGE-ABSENT", "GOVERNANCE", "all-artifacts",
                "consumer coverage evidence is absent", ""));
            return;
        }
        for (ConsumerCoverage item : coverage) {
            Set<String> missing = item.missingExecutors();
            if (!missing.isEmpty()) {
                findings.add(blocking("BRX-P6-COVERAGE-MISSING", "GOVERNANCE", item.artifact(),
                    "artifact did not execute required executors " + missing, item.artifact()));
            }
        }
    }

    private void evaluateEvidence(
        List<ArchitectureEvidence> evidence,
        List<ArchitectureWaiver> waivers,
        AcceptanceMode mode,
        Instant now,
        List<EvidenceFinding> findings
    ) {
        if (evidence.isEmpty()) {
            findings.add(blocking("BRX-P6-EVIDENCE-ABSENT", "GOVERNANCE", "all-artifacts",
                "architecture evidence is absent", ""));
            return;
        }
        for (ArchitectureEvidence item : evidence) {
            if (item.selectedTargetCount() == 0 && item.status() != EvidenceStatus.NOT_APPLICABLE) {
                findings.add(blocking("BRX-P6-TARGET-EMPTY", item.requirementId(), item.artifact(),
                    "executor selected zero targets for an applicable requirement", item.evidenceUri()));
                continue;
            }
            if (item.isExpired(now)) {
                handleWaivableFinding(item, waivers, mode, now, findings,
                    "BRX-P6-EVIDENCE-EXPIRED", "evidence expired at " + item.expiresAt());
                continue;
            }
            if (!item.status().isCleanPass()) {
                handleWaivableFinding(item, waivers, mode, now, findings,
                    "BRX-P6-REQUIREMENT-NOT-PASSING",
                    "requirement status is " + item.status().wireValue());
            }
        }
    }

    private void evaluateWaivers(
        List<ArchitectureWaiver> waivers,
        AcceptanceMode mode,
        Instant now,
        List<EvidenceFinding> findings
    ) {
        for (ArchitectureWaiver waiver : waivers) {
            if (!waiver.isActiveAt(now)) {
                findings.add(blocking("BRX-P6-WAIVER-EXPIRED", waiver.requirementId(), waiver.artifact(),
                    "waiver expired at " + waiver.expiresAt(), waiver.artifact()));
                continue;
            }
            if (mode == AcceptanceMode.IMPLEMENTATION_ACCEPTED) {
                findings.add(blocking("BRX-P6-WAIVER-BLOCKS-IMPLEMENTATION-ACCEPTED",
                    waiver.requirementId(), waiver.artifact(),
                    "Implementation Accepted requires enforced-passing without waivers", waiver.artifact()));
            }
            if (mode == AcceptanceMode.RELEASE_ACCEPTANCE && waiver.priority().blocksGaOrRelease()) {
                findings.add(blocking("BRX-P6-WAIVER-BLOCKS-RELEASE", waiver.requirementId(),
                    waiver.artifact(), "P0/P1 waiver cannot pass release acceptance", waiver.artifact()));
            }
        }
    }

    private void evaluateSupplyChain(
        EvidenceBundle bundle,
        AcceptanceMode mode,
        Instant now,
        List<EvidenceFinding> findings
    ) {
        Set<String> coveredArtifacts = new LinkedHashSet<>();
        bundle.supplyChain().forEach(item -> coveredArtifacts.add(item.artifact()));

        for (ConsumerCoverage coverage : bundle.coverage()) {
            if (!coveredArtifacts.contains(coverage.artifact())) {
                findings.add(blocking("BRX-P6-SUPPLY-ABSENT", "S-10", coverage.artifact(),
                    "supply-chain evidence is absent for covered artifact", coverage.artifact()));
            }
        }

        for (SupplyChainEvidence supply : bundle.supplyChain()) {
            List<String> missing = supply.missingProofs();
            if (!missing.isEmpty()) {
                handleSupplyFinding(supply, bundle.waivers(), mode, now, findings,
                    "BRX-P6-SUPPLY-MISSING", "missing supply-chain proofs " + missing);
            }
            if (supply.isExpired(now)) {
                handleSupplyFinding(supply, bundle.waivers(), mode, now, findings,
                    "BRX-P6-SUPPLY-EXPIRED", "supply-chain evidence expired at " + supply.expiresAt());
            }
        }
    }

    private void handleWaivableFinding(
        ArchitectureEvidence evidence,
        List<ArchitectureWaiver> waivers,
        AcceptanceMode mode,
        Instant now,
        List<EvidenceFinding> findings,
        String code,
        String message
    ) {
        Optional<ArchitectureWaiver> waiver = activeWaiver(
            waivers, evidence.requirementId(), evidence.artifact(), now);
        if (waiver.isPresent() && mode == AcceptanceMode.CI_GATE) {
            findings.add(new EvidenceFinding(FindingSeverity.WARNING, "BRX-P6-WAIVED",
                evidence.requirementId(), evidence.artifact(),
                message + "; covered by waiver approved by " + waiver.get().approver(),
                evidence.evidenceUri()));
            return;
        }
        if (waiver.isPresent() && mode == AcceptanceMode.RELEASE_ACCEPTANCE
            && !waiver.get().priority().blocksGaOrRelease()) {
            findings.add(new EvidenceFinding(FindingSeverity.WARNING, "BRX-P6-WAIVED",
                evidence.requirementId(), evidence.artifact(),
                message + "; covered by release-eligible waiver approved by " + waiver.get().approver(),
                evidence.evidenceUri()));
            return;
        }
        findings.add(blocking(code, evidence.requirementId(), evidence.artifact(), message, evidence.evidenceUri()));
    }

    private void handleSupplyFinding(
        SupplyChainEvidence supply,
        List<ArchitectureWaiver> waivers,
        AcceptanceMode mode,
        Instant now,
        List<EvidenceFinding> findings,
        String code,
        String message
    ) {
        Optional<ArchitectureWaiver> waiver = activeWaiver(waivers, "S-10", supply.artifact(), now)
            .or(() -> activeWaiver(waivers, "SUPPLY", supply.artifact(), now));
        if (waiver.isPresent() && mode == AcceptanceMode.CI_GATE) {
            findings.add(new EvidenceFinding(FindingSeverity.WARNING, "BRX-P6-SUPPLY-WAIVED",
                "S-10", supply.artifact(),
                message + "; covered by waiver approved by " + waiver.get().approver(),
                supply.provenanceUri()));
            return;
        }
        findings.add(blocking(code, "S-10", supply.artifact(), message, supply.provenanceUri()));
    }

    private Optional<ArchitectureWaiver> activeWaiver(
        List<ArchitectureWaiver> waivers,
        String requirementId,
        String artifact,
        Instant now
    ) {
        return waivers.stream()
            .filter(waiver -> waiver.covers(requirementId, artifact))
            .filter(waiver -> waiver.isActiveAt(now))
            .findFirst();
    }

    private EvidenceFinding blocking(
        String code,
        String requirementId,
        String artifact,
        String message,
        String evidenceUri
    ) {
        return new EvidenceFinding(FindingSeverity.BLOCKING, code, requirementId, artifact, message, evidenceUri);
    }
}
