/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the Phase 6 report formats consumed by CI and release review.
 */
public final class EvidenceReportWriter {

    private final EvidenceReportRenderer renderer;

    public EvidenceReportWriter(EvidenceReportRenderer renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("renderer is required");
        }
        this.renderer = renderer;
    }

    /**
     * Writes JSON, SARIF, and Markdown reports to the supplied directory.
     */
    public void writeAll(EvidenceReport report, Path outputDirectory) throws IOException {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        if (outputDirectory == null) {
            throw new IllegalArgumentException("outputDirectory is required");
        }
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("architecture-evidence.json"),
            renderer.toJson(report), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("architecture-evidence.sarif"),
            renderer.toSarif(report), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("architecture-evidence.md"),
            renderer.toMarkdown(report), StandardCharsets.UTF_8);
    }
}
