/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

/**
 * A request's body is not JSON by its content type (standard-link plan queue item 10, Q10b, D132): it has another
 * content type, or none while it has a body, or a {@code Content-Type} that does not parse. The dispatcher finds out
 * only after the call's access checks; the protocol adapter then answers as the host answers a content type it does
 * not read, {@value #STATUS}.
 *
 * <p>The host writes that answer, so this type names no wire code: the runtime's span and meters record status
 * {@value #STATUS} and the error type {@value #ERROR_TYPE}.</p>
 */
public final class EndpointMediaTypeUnsupportedException extends RuntimeException {

    /** The status the host answers with. */
    public static final int STATUS = 415;

    /** The error type of the call's span and meters; never written to the caller. */
    public static final String ERROR_TYPE = "endpoint.media_type_unsupported";

    private static final long serialVersionUID = 1L;

    private final String contentType;
    private final String method;

    /**
     * Creates the exception.
     *
     * @param contentType the request's {@code Content-Type} as it came, or {@code null} when it had none
     * @param method the request's method
     * @param cause why the content type did not parse, or {@code null}
     */
    public EndpointMediaTypeUnsupportedException(String contentType, String method, Throwable cause) {
        super(ERROR_TYPE, cause);
        this.contentType = contentType;
        this.method = method;
    }

    /**
     * Returns the request's {@code Content-Type} as it came.
     *
     * @return the header, or {@code null} when the request had none
     */
    public String contentType() {
        return contentType;
    }

    /**
     * Returns the request's method.
     *
     * @return the method
     */
    public String method() {
        return method;
    }
}
