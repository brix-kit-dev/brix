/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.List;
import java.util.Map;

import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.query.BrixQueryException;

/**
 * How a failed endpoint call is answered (root BRX-RS-MANIFEST-022, ERROR-010, ERROR-012). The controller writes the
 * answer; the dispatcher records the same status and code on the call's span and meters.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
final class EndpointFailures {

    /** The code of every failure the caller is not told about. */
    static final String UNKNOWN = "platform.unknown";

    private EndpointFailures() {
    }

    /**
     * Maps a failure.
     *
     * @param failure what the dispatcher or the handler threw
     * @return the answer
     */
    static Failure of(RuntimeException failure) {
        if (failure instanceof EndpointHandlingException handling) {
            return new Failure(handling.status(), handling.errorCode(), handling.getMessage(), List.of(), false);
        }
        if (failure instanceof BrixQueryException query) {
            return known(switch (query.code()) {
                case PROVIDER_MISSING, REJECTED, CIRCUIT_OPEN -> 503;
                case DEADLINE_EXCEEDED -> 504;
                case ACCESS_DENIED -> 403;
                case INVALID_REQUEST, SERIALIZATION_FAILED, PROVIDER_FAILED -> 500;
            }, query.is(BrixQueryException.Code.PROVIDER_FAILED) ? UNKNOWN : query.code().wireCode());
        }
        if (failure instanceof EndpointRequestInvalidException invalid) {
            // Standard-link plan L7.2: the paths and rules the request breaks, never its values.
            return new Failure(400, EndpointRequestInvalidException.CODE, EndpointRequestInvalidException.CODE,
                invalid.violations(), false);
        }
        if (failure instanceof EndpointDeadlineExceededException) {
            return known(504, EndpointDeadlineExceededException.CODE);
        }
        if (failure instanceof EndpointRejectedException) {
            return known(503, EndpointRejectedException.CODE);
        }
        if (failure instanceof EndpointResponseInvalidException) {
            return known(500, UNKNOWN);
        }
        if (failure instanceof EndpointDispatchException) {
            return known(404, "endpoint.not_found");
        }
        if (failure instanceof BrixException brix && brix.errorCode() != null) {
            // Root BRX-RS-MANIFEST-022: an expected failure answers with its code, at the status of its category.
            return known(status(brix.errorCode().category()), brix.errorCode().wireCode());
        }
        // Root BRX-RS-MANIFEST-022, ERROR-012: anything else is platform.unknown, with the cause in the log only.
        return new Failure(500, UNKNOWN, UNKNOWN, List.of(), true);
    }

    /**
     * Returns the HTTP status of an error category.
     *
     * @param category the category
     * @return the status
     */
    static int status(ErrorCategory category) {
        return switch (category) {
            case INVALID -> 400;
            case DENIED -> 403;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case UNAVAILABLE -> 503;
            case DEADLINE_EXCEEDED -> 504;
            case INTERNAL -> 500;
        };
    }

    /**
     * Returns the meter outcome of an answer.
     *
     * @param status the HTTP status
     * @param code the error's wire code, or {@code null} on success
     * @return one of the outcomes of {@link EndpointMetrics#finished}
     */
    static String outcome(int status, String code) {
        if (code == null) {
            return "ok";
        }
        return switch (status) {
            case 401, 403 -> "denied";
            case 404 -> "not_found";
            case 409 -> "conflict";
            case 503 -> EndpointRejectedException.CODE.equals(code) ? "rejected" : "unavailable";
            case 504 -> "deadline_exceeded";
            default -> status < 500 ? "invalid" : "failed";
        };
    }

    private static Failure known(int status, String code) {
        return new Failure(status, code, code, List.of(), false);
    }

    /**
     * The answer to a failed call.
     *
     * @param status the HTTP status
     * @param code the wire code
     * @param message the message of the error envelope
     * @param violations the request's schema violations, for {@code endpoint.invalid_request}
     * @param unexpected whether the cause belongs in the log
     */
    record Failure(int status, String code, String message, List<Map<String, String>> violations,
            boolean unexpected) {
    }
}
