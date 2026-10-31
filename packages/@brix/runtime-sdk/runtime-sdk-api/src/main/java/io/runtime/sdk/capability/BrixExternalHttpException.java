/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.util.Map;

import io.runtime.sdk.annotation.Since;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.error.ErrorCode;

/**
 * A call to an external service got no answer (root BRX-RS-ERROR-012, ERROR-014; standard-link plan L8.1, D62).
 *
 * <p>An answer with any status is a response, not a failure: the caller reads its status. Only a call the runtime
 * refused, or one that got no answer, fails, each with its own stable code.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public final class BrixExternalHttpException extends BrixException {

    private static final long serialVersionUID = 1L;

    /** Stable external call error codes. */
    public enum Code implements ErrorCode {
        /** The plugin does not declare the service under {@code externalServices}, or the host has no address for it. */
        UNDECLARED_SERVICE("external_http.undeclared_service", ErrorCategory.INTERNAL),
        /** The service's address resolves to this host, or to an address no plugin may call. */
        TARGET_FORBIDDEN("external_http.target_forbidden", ErrorCategory.INTERNAL),
        /** No answer came before the deadline: the call's own, or the one of the call the plugin is handling. */
        DEADLINE_EXCEEDED("external_http.deadline_exceeded", ErrorCategory.DEADLINE_EXCEEDED),
        /** Calls to the service have failed so often that the runtime does not try for a while. */
        CIRCUIT_OPEN("external_http.circuit_open", ErrorCategory.UNAVAILABLE),
        /** The plugin already makes as many calls to the service as the host allows. */
        REJECTED("external_http.rejected", ErrorCategory.UNAVAILABLE),
        /** The connection or the exchange failed, or the answer was larger than the host allows. */
        FAILED("external_http.failed", ErrorCategory.UNAVAILABLE);

        private final String wireCode;
        private final ErrorCategory category;

        Code(String wireCode, ErrorCategory category) {
            this.wireCode = wireCode;
            this.category = category;
        }

        @Override
        public String wireCode() {
            return wireCode;
        }

        @Override
        public ErrorCategory category() {
            return category;
        }
    }

    private final Code code;

    /**
     * Creates the failure.
     *
     * @param code stable code
     * @param serviceId the service the plugin called
     * @param cause internal cause, kept for logs only
     */
    public BrixExternalHttpException(Code code, String serviceId, Throwable cause) {
        super(code, Map.of("service", serviceId == null ? "" : serviceId), cause);
        this.code = code;
    }

    /**
     * Returns the stable code.
     *
     * @return the code
     */
    public Code code() {
        return code;
    }

    /**
     * Returns whether this failure has the given code.
     *
     * @param expected the code to compare with
     * @return {@code true} when the codes are the same
     */
    public boolean is(Code expected) {
        return code == expected;
    }
}
