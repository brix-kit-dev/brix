/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.executor;

import java.util.List;

/**
 * Raised when the Java static boundary executor finds blocking violations.
 */
public final class JavaStaticBoundaryViolationException extends RuntimeException {

    private final List<JavaStaticBoundaryRuleResult> failures;

    public JavaStaticBoundaryViolationException(List<JavaStaticBoundaryRuleResult> failures) {
        super(formatMessage(failures));
        this.failures = List.copyOf(failures);
    }

    public List<JavaStaticBoundaryRuleResult> failures() {
        return failures;
    }

    private static String formatMessage(List<JavaStaticBoundaryRuleResult> failures) {
        return "Java static boundary violations: " + failures.stream()
            .map(result -> result.requirementId() + "[" + result.diagnosticCode() + "]")
            .toList();
    }
}
