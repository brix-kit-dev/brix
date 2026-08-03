/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.evidence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Records which executors were required and actually executed for an artifact.
 */
public record ConsumerCoverage(
    String artifact,
    List<String> requiredExecutors,
    List<String> executedExecutors
) {

    public ConsumerCoverage {
        requireText(artifact, "artifact");
        requiredExecutors = copyTextList(requiredExecutors, "requiredExecutors");
        executedExecutors = copyTextList(executedExecutors, "executedExecutors");
    }

    /**
     * Returns required executors that were not executed.
     */
    public Set<String> missingExecutors() {
        Set<String> missing = new LinkedHashSet<>(requiredExecutors);
        missing.removeAll(executedExecutors);
        return missing;
    }

    private static List<String> copyTextList(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        for (String value : values) {
            requireText(value, field + " item");
        }
        return List.copyOf(values);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
