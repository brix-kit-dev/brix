/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.query;

import java.util.Map;
import java.util.regex.Pattern;

import io.runtime.sdk.annotation.Since;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCode;

/**
 * A typed query could not be answered (root BRX-RS-MESSAGE-007, ERROR-008, ERROR-012).
 *
 * <p>Finding nothing is not a failure: a query that may find nothing returns {@code Optional.empty()}. Every
 * failure has its own stable code, so a missing provider, a passed deadline or an open circuit never looks like
 * an empty result (ERROR-009).</p>
 *
 * <p>A provider throws one itself only for a request it cannot answer, through {@link #invalidRequest(String)};
 * every other failure is classified by the runtime.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public final class BrixQueryException extends BrixException {

    private static final long serialVersionUID = 1L;

    private static final Pattern REASON = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    /** Stable query error codes. */
    public enum Code implements ErrorCode {
        /** No plugin on this host provides the query, or its provider has stopped ({@code PROVIDER_MISSING}). */
        PROVIDER_MISSING("query.provider_missing"),
        /** The provider's access policy refused the caller, its user or its scope. */
        ACCESS_DENIED("query.access_denied"),
        /** The request does not satisfy the query's contract. */
        INVALID_REQUEST("query.invalid_request"),
        /** The deadline passed before or while the provider answered. */
        DEADLINE_EXCEEDED("query.deadline_exceeded"),
        /** The call was not admitted: no capacity, the host is draining, no admitted call, or nested too deep. */
        REJECTED("query.rejected"),
        /** The circuit breaker of this caller and query is open. */
        CIRCUIT_OPEN("query.circuit_open"),
        /** The provider answered with something other than the contract's result type. */
        SERIALIZATION_FAILED("query.serialization_failed"),
        /** The provider failed, or could not reach its data for now. */
        PROVIDER_FAILED("query.provider_failed");

        private final String wireCode;

        Code(String wireCode) {
            this.wireCode = wireCode;
        }

        @Override
        public String wireCode() {
            return wireCode;
        }
    }

    private final Code code;

    /**
     * Creates the failure.
     *
     * @param code stable code
     * @param safeParameters non-sensitive parameters: the query type and a reason from a fixed set
     * @param cause internal cause, kept for logs only
     */
    public BrixQueryException(Code code, Map<String, String> safeParameters, Throwable cause) {
        super(code, safeParameters, cause);
        this.code = code;
    }

    /**
     * Creates the failure a provider throws for a request it cannot answer.
     *
     * @param reason a fixed, non-sensitive token such as {@code order-id-malformed}; never a value from the request
     * @return the failure, with code {@link Code#INVALID_REQUEST}
     */
    public static BrixQueryException invalidRequest(String reason) {
        if (reason == null || !REASON.matcher(reason).matches()) {
            throw new IllegalArgumentException("reason must be a short lower-case token: " + reason);
        }
        return new BrixQueryException(Code.INVALID_REQUEST, Map.of("reason", reason), null);
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
