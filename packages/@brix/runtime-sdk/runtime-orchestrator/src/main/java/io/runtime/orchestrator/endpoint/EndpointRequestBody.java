/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.function.Supplier;

import tools.jackson.databind.JsonNode;

/**
 * The body of a request to a Runtime Shell endpoint, read only when the dispatcher asks for it (root
 * BRX-RS-MANIFEST-022, 3.4.0; standard-link plan queue item 10, Q10b, D130). The protocol adapter hands it over
 * unread; the dispatcher reads it after the authentication, tenant context and permission checks, as the first step
 * of the request's normalization, so a call those checks refuse never has its body read.
 *
 * <p>A body is JSON: {@code application/json} or {@code application/*+json}. A body that is not JSON text fails with
 * {@link EndpointRequestInvalidException} (D131); one of another content type fails with
 * {@link EndpointMediaTypeUnsupportedException} (D132).</p>
 *
 * @author Runtime SDK Team
 * @since 3.4.0
 */
public interface EndpointRequestBody {

    /**
     * Reads the body as the host's JSON mapper reads it, which a handler of the old shape gets: a decimal is a
     * {@code Double}.
     *
     * @return the body; {@code null} when the request has none
     * @throws EndpointRequestInvalidException when the body is not JSON text
     * @throws EndpointMediaTypeUnsupportedException when its content type is not JSON
     */
    Object value();

    /**
     * Reads the body into a tree that keeps every decimal digit the client wrote (queue item 7, D123), which a typed
     * handler's request is built from. It is asked for only after {@link #value()} has read the body.
     *
     * @return the tree; {@code null} when the body came already read, without its text
     */
    JsonNode exact();

    /**
     * Returns a body read already, as a dispatcher that takes one passes it on.
     *
     * @param value the body as the host's JSON mapper read it, or {@code null}
     * @param exact reads the same text with every decimal digit; {@code null} when there is no text
     * @return the body
     */
    static EndpointRequestBody read(Object value, Supplier<JsonNode> exact) {
        return new EndpointRequestBody() {
            @Override
            public Object value() {
                return value;
            }

            @Override
            public JsonNode exact() {
                return exact == null ? null : exact.get();
            }
        };
    }
}
