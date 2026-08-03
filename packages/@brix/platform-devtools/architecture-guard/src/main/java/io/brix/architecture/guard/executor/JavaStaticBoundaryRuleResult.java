/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.executor;

/**
 * Result emitted for one Java static boundary requirement.
 *
 * @param requirementId stable architecture requirement id
 * @param targetCardinality number of bytecode classes checked by the requirement
 * @param passed true when the requirement passed
 * @param diagnosticCode stable diagnostic code
 * @param message concise diagnostic message
 */
public record JavaStaticBoundaryRuleResult(
    String requirementId,
    long targetCardinality,
    boolean passed,
    String diagnosticCode,
    String message
) {
}
