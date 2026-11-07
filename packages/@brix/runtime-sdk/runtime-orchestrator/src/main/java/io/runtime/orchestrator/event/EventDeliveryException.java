/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import io.runtime.sdk.capability.PersistenceCapabilityException;

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

    /**
     * Creates the failure of a delivery that failed once its subscriber was found. It is the handler's failure, whatever
     * the handler could not reach, unless the persistence adapter says that the database cannot be reached: the message
     * then waits for the owner (Kafka design §14 K11). All owners share one database, so that holds through whichever
     * owner the failure came, such as the read that binds a policy subscription's scope before the subscriber's unit of
     * work begins. A service the handler calls being down spends attempts like any other failure: nothing says that it
     * will come back (standard-link plan D90).
     *
     * @param handlerFailed the stable error code of a failed handler
     * @param message error message
     * @param failure what ended the delivery
     * @return the delivery failure
     */
    public static EventDeliveryException whileHandling(String handlerFailed, String message, RuntimeException failure) {
        String code = databaseIsOutOfReach(failure) ? SubscriptionDelivery.OWNER_UNAVAILABLE : handlerFailed;
        return new EventDeliveryException(code, message, failure, false);
    }

    private static boolean databaseIsOutOfReach(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PersistenceCapabilityException persistence
                    && persistence.errorCode() == PersistenceCapabilityException.Code.UNAVAILABLE) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean permanent() {
        return permanent;
    }
}
