/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.util.Objects;

/**
 * Records an operator's action on a message in the platform audit before the action takes effect (root
 * BRX-RS-MESSAGE-028; Kafka design D33, §14 K14): replaying or discarding a parked message, republishing a published
 * one. The runtime custodian {@value #CUSTODIAN} writes into the table the platform audit owner registers for it;
 * without that registration the management endpoints refuse every such action.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface MessageOperationAudit {

    /** The runtime custodian that writes these records (root BRX-RS-DATA-045). */
    String CUSTODIAN = "runtime.message-operations";

    /** A parked message was delivered again. */
    String REPLAYED = "MESSAGE_REPLAYED";

    /** A parked message was dropped for good. */
    String DISCARDED = "MESSAGE_DISCARDED";

    /** A published message was sent again. */
    String REPUBLISHED = "MESSAGE_REPUBLISHED";

    /** Refuses every action: the Host has no platform audit to record it in. */
    MessageOperationAudit NONE = operation -> {
        throw new IllegalStateException("No platform audit is registered for " + CUSTODIAN);
    };

    /**
     * Records the action. It fails when the record cannot be written, and the action must then not be taken.
     *
     * @param operation what is done to which message, by whom and why
     */
    void record(MessageOperation operation);

    /**
     * An operator's action on one message.
     *
     * @param action {@link #REPLAYED}, {@link #DISCARDED} or {@link #REPUBLISHED}
     * @param messageId the event or command id
     * @param place the outbox table or the subscription
     * @param operator who asked, as named in the request
     * @param principal whom the management port authenticated, or {@code null}
     * @param reason why
     * @param errorCode why the action failed after it was recorded, or {@code null} for the request itself
     */
    record MessageOperation(String action, String messageId, String place, String operator, String principal,
            String reason, String errorCode) {

        /** Texts longer than this are cut. */
        static final int MAX_TEXT = 200;

        /** Longest message id the audit table holds. */
        static final int MAX_MESSAGE_ID = 128;

        /** Longest error code the audit table holds. */
        static final int MAX_ERROR_CODE = 64;

        /**
         * Creates a record; the operator and the reason are required (D33).
         */
        public MessageOperation {
            action = require(action, "action");
            messageId = require(messageId, "messageId");
            place = require(place, "place");
            operator = require(operator, "operator");
            reason = require(reason, "reason");
            if (messageId.length() > MAX_MESSAGE_ID) {
                throw new IllegalArgumentException("messageId is longer than " + MAX_MESSAGE_ID);
            }
            principal = principal == null || principal.isBlank() ? null : cut(principal);
            errorCode = errorCode == null || errorCode.isBlank() ? null
                : errorCode.length() > MAX_ERROR_CODE ? errorCode.substring(0, MAX_ERROR_CODE) : errorCode;
        }

        /**
         * Creates the record of a request.
         *
         * @param action {@link #REPLAYED}, {@link #DISCARDED} or {@link #REPUBLISHED}
         * @param messageId the event or command id
         * @param place the outbox table or the subscription
         * @param operator who asked
         * @param principal whom the management port authenticated, or {@code null}
         * @param reason why
         * @return the record
         */
        public static MessageOperation of(String action, String messageId, String place, String operator,
                String principal, String reason) {
            return new MessageOperation(action, messageId, place, operator, principal, reason, null);
        }

        /**
         * Returns the record of this request having failed.
         *
         * @param code why it failed
         * @return the record of the failure
         */
        public MessageOperation failed(String code) {
            return new MessageOperation(action, messageId, place, operator, principal, reason,
                Objects.requireNonNull(code, "code must not be null"));
        }

        private static String require(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return cut(value.strip());
        }

        private static String cut(String value) {
            return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
        }
    }
}
