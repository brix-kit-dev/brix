/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.runtime.sdk.capability.ExternalHttpResponse;

/**
 * Sends one external request once (standard-link plan L8.4). The gateway around it owns the target policy, the
 * deadline, the limits, the retries and the trace; a transport only moves bytes.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface ExternalHttpTransport {

    /**
     * Sends a request and reads the answer.
     *
     * @param method the method
     * @param target the absolute URL, query included
     * @param headers the headers to send, the trace context included
     * @param body the body, empty when there is none
     * @param timeout how long the exchange may take, connection included
     * @param maxResponseBytes the largest body to read
     * @return the answer
     * @throws java.net.http.HttpTimeoutException when the exchange takes longer than {@code timeout}
     * @throws ResponseTooLargeException when the body is larger than {@code maxResponseBytes}
     * @throws IOException when the connection or the exchange fails
     * @throws InterruptedException when the waiting thread is interrupted
     */
    ExternalHttpResponse send(String method, URI target, Map<String, List<String>> headers, byte[] body,
        Duration timeout, long maxResponseBytes) throws IOException, InterruptedException;

    /** An answer's body was larger than the host allows. */
    final class ResponseTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates the failure.
         *
         * @param maxResponseBytes the limit
         */
        public ResponseTooLargeException(long maxResponseBytes) {
            super("the answer is larger than " + maxResponseBytes + " bytes");
        }
    }
}
