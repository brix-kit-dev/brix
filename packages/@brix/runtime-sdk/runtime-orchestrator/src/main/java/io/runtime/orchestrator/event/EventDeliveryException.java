/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * A delivery that did not reach the subscriber's commit, with its stable error code (Kafka design §4.1).
 * <p>A permanent failure cannot pass without an operator, so the transport parks the event at once; any
 * other failure may pass, so the transport retries it.</p>
 */
public class EventDeliveryException extends RuntimeException {

    private final String errorCode;
    private final boolean permanent;

    /**
     * Creates a delivery failure.
     *
     * @param errorCode stable error code
     * @param message error message
     * @param cause original cause
     * @param permanent whether a retry cannot succeed without operator action
     */
    public EventDeliveryException(String errorCode, String message, Throwable cause, boolean permanent) {
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
