/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.util.Map;

import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCode;

/**
 * Stable unchecked exception for typed command submission failures.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class CommandSubmitException extends BrixException {

    /**
     * Stable command error codes.
     */
    public enum Code implements ErrorCode {
        /**
         * Sender command validation failed.
         */
        INVALID_COMMAND("command.invalid"),

        /**
         * A durable command was submitted without an active sender transaction.
         */
        NO_ACTIVE_TRANSACTION("command.no_active_transaction"),

        /**
         * The sender transaction rolled back after the outbox append was staged.
         */
        TRANSACTION_ROLLED_BACK("command.transaction_rolled_back"),

        /**
         * The canonical outbox append failed.
         */
        OUTBOX_APPEND_FAILED("command.outbox_append_failed"),

        /**
         * Runtime rejected delivery because the command handler is unavailable.
         */
        HANDLER_OFFLINE("command.handler_offline");

        private final String wireCode;

        Code(String wireCode) {
            this.wireCode = wireCode;
        }

        @Override
        public String wireCode() {
            return wireCode;
        }
    }

    /**
     * Creates a command submit exception.
     *
     * @param errorCode stable error code
     * @param safeParameters non-sensitive safe parameters
     * @param cause internal cause
     */
    public CommandSubmitException(Code errorCode, Map<String, String> safeParameters, Throwable cause) {
        super(errorCode, safeParameters, cause);
    }
}
