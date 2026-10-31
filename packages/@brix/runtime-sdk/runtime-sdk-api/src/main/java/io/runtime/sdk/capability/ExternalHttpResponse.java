/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * An external service's answer, whatever its status (standard-link plan L8.1).
 *
 * @param status the HTTP status
 * @param headers the headers, names in lower case
 * @param body the body, empty when there is none
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public record ExternalHttpResponse(int status, Map<String, List<String>> headers, byte[] body) {

    /**
     * Copies the answer.
     */
    public ExternalHttpResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be between 100 and 599: " + status);
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, values) -> {
                if (name != null && values != null) {
                    copy.put(name.toLowerCase(Locale.ROOT), List.copyOf(values));
                }
            });
        }
        headers = Map.copyOf(copy);
        body = body == null ? new byte[0] : body.clone();
    }

    /**
     * Tells whether the status is 2xx.
     *
     * @return whether the service did what was asked
     */
    public boolean successful() {
        return status >= 200 && status < 300;
    }

    /**
     * Returns the body as UTF-8 text.
     *
     * @return the text
     */
    public String text() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Returns the first value of a header.
     *
     * @param name the header, in any case
     * @return its first value
     */
    public Optional<String> header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /**
     * Returns a copy of the body.
     *
     * @return the body, empty when there is none
     */
    @Override
    public byte[] body() {
        return body.clone();
    }
}
