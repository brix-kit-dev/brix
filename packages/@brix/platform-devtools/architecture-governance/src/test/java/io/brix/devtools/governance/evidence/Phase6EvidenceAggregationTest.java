/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Phase6EvidenceAggregationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void acceptsCompleteEvidenceAndWritesJsonSarifAndMarkdownReports() throws IOException {
        EvidenceReport report = new Phase6EvidenceAggregator()
            .aggregate(completeBundle(List.of()), AcceptanceMode.IMPLEMENTATION_ACCEPTED, NOW);

        assertTrue(report.accepted());
        String json = new EvidenceReportRenderer().toJson(report);
        String sarif = new EvidenceReportRenderer().toSarif(report);
        String markdown = new EvidenceReportRenderer().toMarkdown(report);

        assertTrue(json.contains("\"accepted\":true"));
        assertTrue(sarif.contains("\"version\":\"2.1.0\""));
        assertTrue(markdown.contains("Architecture Evidence Report"));

        Path output = Path.of("target/phase6-evidence");
        assertDoesNotThrow(() -> new EvidenceReportWriter(new EvidenceReportRenderer()).writeAll(report, output));
        assertTrue(Files.exists(output.resolve("architecture-evidence.json")));
        assertTrue(Files.exists(output.resolve("architecture-evidence.sarif")));
        assertTrue(Files.exists(output.resolve("architecture-evidence.md")));
    }

    @Test
    void blocksMissingConsumerCoverageEvenWhenOtherEvidencePasses() {
        EvidenceBundle bundle = new EvidenceBundle(
            "runtime-shell@3.0.10",
            "0123456789abcdef",
            completeBundle(List.of()).evidence(),
            List.of(new ConsumerCoverage("io.brix:booking-core", List.of("java-static-boundary", "supply-chain-gate"),
                List.of("java-static-boundary"))),
            completeBundle(List.of()).supplyChain(),
            List.of());

        EvidenceReport report = new Phase6EvidenceAggregator()
            .aggregate(bundle, AcceptanceMode.CI_GATE, NOW);

        assertFalse(report.accepted());
        assertTrue(report.findings().stream()
            .anyMatch(finding -> finding.code().equals("BRX-P6-COVERAGE-MISSING")));
    }

    @Test
    void blocksExpiredWaiverInCiMode() {
        ArchitectureWaiver expired = new ArchitectureWaiver(
            "A-7",
            "io.brix:booking-ui-web",
            "frontend",
            "browser evidence migration window",
            "route boundary proof is incomplete",
            "manual browser review attached to the pull request",
            NOW.minus(Duration.ofDays(1)),
            "security-reviewer",
            RequirementPriority.P2);

        EvidenceReport report = new Phase6EvidenceAggregator()
            .aggregate(completeBundle(List.of(expired)), AcceptanceMode.CI_GATE, NOW);

        assertFalse(report.accepted());
        assertTrue(report.findings().stream()
            .anyMatch(finding -> finding.code().equals("BRX-P6-WAIVER-EXPIRED")));
    }

    @Test
    void allowsActiveWaiverForCiButNotForImplementationAccepted() {
        ArchitectureWaiver active = new ArchitectureWaiver(
            "A-19",
            "io.brix:case-ui-web",
            "frontend",
            "federation runtime identity evidence is being backfilled",
            "runtime singleton drift would be detected during release review",
            "release is blocked until browser evidence is attached",
            NOW.plus(Duration.ofDays(7)),
            "architecture-reviewer",
            RequirementPriority.P2);
        EvidenceBundle bundle = withNonPassingEvidence(active);

        EvidenceReport ciReport = new Phase6EvidenceAggregator()
            .aggregate(bundle, AcceptanceMode.CI_GATE, NOW);
        EvidenceReport implementationReport = new Phase6EvidenceAggregator()
            .aggregate(bundle, AcceptanceMode.IMPLEMENTATION_ACCEPTED, NOW);

        assertTrue(ciReport.accepted());
        assertFalse(implementationReport.accepted());
        assertTrue(implementationReport.findings().stream()
            .anyMatch(finding -> finding.code().equals("BRX-P6-WAIVER-BLOCKS-IMPLEMENTATION-ACCEPTED")));
    }

    @Test
    void blocksMissingSupplyChainProofs() {
        EvidenceBundle bundle = new EvidenceBundle(
            "runtime-shell@3.0.10",
            "0123456789abcdef",
            completeBundle(List.of()).evidence(),
            completeBundle(List.of()).coverage(),
            List.of(new SupplyChainEvidence(
                "io.brix:booking-core",
                false,
                "",
                "",
                "target/license.json",
                "target/sca.json",
                "sha1:bad",
                "",
                "",
                false,
                NOW.minus(Duration.ofHours(1)),
                Duration.ofDays(1))),
            List.of());

        EvidenceReport report = new Phase6EvidenceAggregator()
            .aggregate(bundle, AcceptanceMode.CI_GATE, NOW);

        assertFalse(report.accepted());
        assertTrue(report.findings().stream()
            .anyMatch(finding -> finding.code().equals("BRX-P6-SUPPLY-MISSING")));
    }

    @Test
    void evidenceSchemaDeclaresPhase6Fields() throws IOException {
        String schema = Files.readString(Path.of(
            "src/main/resources/io/brix/devtools/governance/evidence.schema.yaml"));

        for (String required : List.of(
            "baselineId", "commitId", "coverage", "supplyChain", "waivers",
            "selectedTargetCount", "sbomUri", "licenseReportUri", "scaReportUri",
            "signatureUri", "provenanceUri", "sarif")) {
            assertTrue(schema.contains(required), "schema must declare " + required);
        }
    }

    private EvidenceBundle withNonPassingEvidence(ArchitectureWaiver waiver) {
        return new EvidenceBundle(
            "runtime-shell@3.0.10",
            "0123456789abcdef",
            List.of(new ArchitectureEvidence(
                "A-19",
                "io.brix:case-ui-web",
                "runtime-identity-browser",
                EvidenceStatus.MISSING_BLOCKING,
                1,
                "target/runtime-identity.json",
                NOW.minus(Duration.ofHours(1)),
                Duration.ofDays(1),
                List.of("runtime identity report is not attached"))),
            List.of(new ConsumerCoverage("io.brix:case-ui-web",
                List.of("runtime-identity-browser", "supply-chain-gate"),
                List.of("runtime-identity-browser", "supply-chain-gate"))),
            List.of(validSupplyChain("io.brix:case-ui-web")),
            List.of(waiver));
    }

    private EvidenceBundle completeBundle(List<ArchitectureWaiver> waivers) {
        return new EvidenceBundle(
            "runtime-shell@3.0.10",
            "0123456789abcdef",
            List.of(
                new ArchitectureEvidence("A-7", "io.brix:booking-ui-web", "frontend-layer-boundary",
                    EvidenceStatus.ENFORCED_PASSING, 8, "target/frontend-layer-boundary.json",
                    NOW.minus(Duration.ofHours(1)), Duration.ofDays(1), List.of()),
                new ArchitectureEvidence("S-10", "io.brix:booking-core", "supply-chain-gate",
                    EvidenceStatus.ENFORCED_PASSING, 1, "target/supply-chain.json",
                    NOW.minus(Duration.ofHours(1)), Duration.ofDays(1), List.of())),
            List.of(
                new ConsumerCoverage("io.brix:booking-ui-web",
                    List.of("frontend-layer-boundary", "supply-chain-gate"),
                    List.of("frontend-layer-boundary", "supply-chain-gate")),
                new ConsumerCoverage("io.brix:booking-core",
                    List.of("java-static-boundary", "supply-chain-gate"),
                    List.of("java-static-boundary", "supply-chain-gate"))),
            List.of(validSupplyChain("io.brix:booking-ui-web"), validSupplyChain("io.brix:booking-core")),
            waivers);
    }

    private SupplyChainEvidence validSupplyChain(String artifact) {
        return new SupplyChainEvidence(
            artifact,
            true,
            "target/lock-integrity.json",
            "target/sbom.spdx.json",
            "target/license.json",
            "target/sca.json",
            "sha256:0123456789abcdef",
            "target/artifact.sigstore.json",
            "target/provenance.sigstore.json",
            true,
            NOW.minus(Duration.ofHours(1)),
            Duration.ofDays(1));
    }
}
