/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

/**
 * Stable transport failure for the L2B outbox relay.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class OutboxTransportException extends RuntimeException {

    private final String errorCode;
    private final boolean permanent;

    /**
     * Creates a transient transport exception.
     *
     * @param errorCode stable error code
     * @param message error message
     * @param cause original cause
     */
    public OutboxTransportException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, false);
    }

    /**
     * Creates a transport exception.
     *
     * @param errorCode stable error code
     * @param message error message
     * @param cause original cause
     * @param permanent whether retry cannot succeed without operator action
     */
    public OutboxTransportException(String errorCode, String message, Throwable cause, boolean permanent) {
        super(message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
        this.permanent = permanent;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean permanent() {
        return permanent;
    }
}
