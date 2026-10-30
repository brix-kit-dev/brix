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
    private final boolean unavailable;

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
        this(errorCode, message, cause, permanent, false);
    }

    private OutboxTransportException(String errorCode, String message, Throwable cause, boolean permanent,
            boolean unavailable) {
        super(message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
        this.permanent = permanent;
        this.unavailable = unavailable;
    }

    /**
     * Creates the exception of a delivery the receiving side cannot take now through no fault of the message, such as
     * a subscriber that has not started or whose database is down (Kafka design §14 K11). The relay hands the message
     * back without spending an attempt and tries it again later.
     *
     * @param errorCode stable error code
     * @param message error message
     * @param cause original cause
     * @return the exception
     */
    public static OutboxTransportException unavailable(String errorCode, String message, Throwable cause) {
        return new OutboxTransportException(errorCode, message, cause, false, true);
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean permanent() {
        return permanent;
    }

    /**
     * Whether the receiving side could not take the message now, which costs the message no attempt.
     *
     * @return whether the delivery should wait
     */
    public boolean unavailable() {
        return unavailable;
    }
}
