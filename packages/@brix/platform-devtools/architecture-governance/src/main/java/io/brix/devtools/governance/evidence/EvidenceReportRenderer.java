/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Renders stable Phase 6 evidence reports without requiring runtime JSON
 * dependencies.
 */
public final class EvidenceReportRenderer {

    /**
     * Renders the report as deterministic JSON.
     */
    public String toJson(EvidenceReport report) {
        String findings = report.findings().stream()
            .sorted(Comparator.comparing(EvidenceFinding::code)
                .thenComparing(EvidenceFinding::artifact)
                .thenComparing(EvidenceFinding::requirementId))
            .map(this::findingJson)
            .collect(Collectors.joining(","));

        return "{"
            + "\"schemaVersion\":1,"
            + "\"baselineId\":\"" + json(report.baselineId()) + "\","
            + "\"commitId\":\"" + json(report.commitId()) + "\","
            + "\"mode\":\"" + report.mode().name() + "\","
            + "\"generatedAt\":\"" + report.generatedAt() + "\","
            + "\"accepted\":" + report.accepted() + ","
            + "\"blockingFindingCount\":" + report.blockingFindingCount() + ","
            + "\"evidenceCount\":" + report.evidence().size() + ","
            + "\"supplyChainCount\":" + report.supplyChain().size() + ","
            + "\"findings\":[" + findings + "]"
            + "}";
    }

    /**
     * Renders the report as SARIF 2.1.0 so source hosting systems can ingest it.
     */
    public String toSarif(EvidenceReport report) {
        String results = report.findings().stream()
            .map(this::sarifResult)
            .collect(Collectors.joining(","));
        return "{"
            + "\"version\":\"2.1.0\","
            + "\"$schema\":\"https://json.schemastore.org/sarif-2.1.0.json\","
            + "\"runs\":[{"
            + "\"tool\":{\"driver\":{\"name\":\"brix-phase6-evidence-gate\","
            + "\"informationUri\":\"https://github.com/brix-platform/brix\"}},"
            + "\"results\":[" + results + "]"
            + "}]"
            + "}";
    }

    /**
     * Renders the report as Markdown for human review.
     */
    public String toMarkdown(EvidenceReport report) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Architecture Evidence Report\n\n");
        markdown.append("- Baseline: `").append(report.baselineId()).append("`\n");
        markdown.append("- Commit: `").append(report.commitId()).append("`\n");
        markdown.append("- Mode: `").append(report.mode()).append("`\n");
        markdown.append("- Accepted: `").append(report.accepted()).append("`\n");
        markdown.append("- Blocking findings: `").append(report.blockingFindingCount()).append("`\n\n");
        markdown.append("| Severity | Code | Requirement | Artifact | Message |\n");
        markdown.append("|---|---|---|---|---|\n");
        for (EvidenceFinding finding : report.findings()) {
            markdown.append("| ")
                .append(finding.severity()).append(" | `")
                .append(finding.code()).append("` | `")
                .append(finding.requirementId()).append("` | `")
                .append(finding.artifact()).append("` | ")
                .append(finding.message().replace("|", "\\|"))
                .append(" |\n");
        }
        return markdown.toString();
    }

    private String findingJson(EvidenceFinding finding) {
        return "{"
            + "\"severity\":\"" + finding.severity().name() + "\","
            + "\"code\":\"" + json(finding.code()) + "\","
            + "\"requirementId\":\"" + json(finding.requirementId()) + "\","
            + "\"artifact\":\"" + json(finding.artifact()) + "\","
            + "\"message\":\"" + json(finding.message()) + "\","
            + "\"evidenceUri\":\"" + json(finding.evidenceUri()) + "\""
            + "}";
    }

    private String sarifResult(EvidenceFinding finding) {
        return "{"
            + "\"ruleId\":\"" + json(finding.code()) + "\","
            + "\"level\":\"" + sarifLevel(finding.severity()) + "\","
            + "\"message\":{\"text\":\"" + json(finding.message()) + "\"},"
            + "\"locations\":[{\"physicalLocation\":{\"artifactLocation\":{\"uri\":\""
            + json(finding.evidenceUri().isBlank() ? finding.artifact() : finding.evidenceUri())
            + "\"}}}]"
            + "}";
    }

    private String sarifLevel(FindingSeverity severity) {
        return switch (severity) {
            case BLOCKING -> "error";
            case WARNING -> "warning";
            case INFO -> "note";
        };
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
