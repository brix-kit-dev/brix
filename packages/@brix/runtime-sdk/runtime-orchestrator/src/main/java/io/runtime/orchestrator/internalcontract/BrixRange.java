/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.internalcontract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal strict Brix Range v1 evaluator.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class BrixRange {

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");
    private static final Pattern RANGE = Pattern.compile("^(>=|>)(\\d+\\.\\d+\\.\\d+) (<|<=)(\\d+\\.\\d+\\.\\d+)$");

    private BrixRange() {
    }

    /**
     * Returns whether an exact semantic version belongs to a Brix Range v1 expression.
     *
     * @param version exact semantic version
     * @param expression exact version or comparator intersection
     * @return match result
     */
    public static boolean contains(String version, String expression) {
        SemVer candidate = parseVersion(version);
        Matcher range = RANGE.matcher(requireText(expression, "expression"));
        if (!range.matches()) {
            return candidate.compareTo(parseVersion(expression)) == 0;
        }
        SemVer lower = parseVersion(range.group(2));
        SemVer upper = parseVersion(range.group(4));
        boolean lowerMatches = ">=".equals(range.group(1))
            ? candidate.compareTo(lower) >= 0
            : candidate.compareTo(lower) > 0;
        boolean upperMatches = "<=".equals(range.group(3))
            ? candidate.compareTo(upper) <= 0
            : candidate.compareTo(upper) < 0;
        return lowerMatches && upperMatches;
    }

    private static SemVer parseVersion(String value) {
        Matcher matcher = VERSION.matcher(requireText(value, "version"));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + value);
        }
        return new SemVer(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3)));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {
        @Override
        public int compareTo(SemVer other) {
            int result = Integer.compare(major, other.major);
            if (result == 0) {
                result = Integer.compare(minor, other.minor);
            }
            if (result == 0) {
                result = Integer.compare(patch, other.patch);
            }
            return result;
        }
    }
}
