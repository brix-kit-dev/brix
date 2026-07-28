/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

/**
 * Stable fail-fast boundary for operational Runtime assembly failures.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class OperationalRuntimeException extends RuntimeException {

    private final String diagnosticCode;

    /**
     * Creates an operational failure.
     *
     * @param diagnosticCode stable diagnostic code
     * @param message safe diagnostic message
     */
    public OperationalRuntimeException(String diagnosticCode, String message) {
        super(message);
        this.diagnosticCode = requireText(diagnosticCode);
    }

    /**
     * Creates an operational failure with an internal cause.
     *
     * @param diagnosticCode stable diagnostic code
     * @param message safe diagnostic message
     * @param cause internal cause
     */
    public OperationalRuntimeException(String diagnosticCode, String message, Throwable cause) {
        super(message, cause);
        this.diagnosticCode = requireText(diagnosticCode);
    }

    /**
     * Returns the stable diagnostic code.
     *
     * @return diagnostic code
     */
    public String diagnosticCode() {
        return diagnosticCode;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("diagnosticCode must not be blank");
        }
        return value;
    }
}
