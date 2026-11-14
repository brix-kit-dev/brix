/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * The body of a servlet request to the {@link RuntimeShellEndpointController}, read only when the dispatcher asks
 * (root BRX-RS-MANIFEST-022, 3.4.0; standard-link plan queue item 10, Q10b, D130 to D132).
 *
 * <p>It reads JSON only, {@code application/json} and {@code application/*+json}, and as Spring's JSON converter read
 * it before: with the host's mapper, in the charset the content type names (UTF-8 when it names none), an empty body
 * being no body. A typed endpoint's tree keeps every decimal digit (queue item 7, D123). A body that is not JSON text
 * is an invalid request (D131). A body of another content type is not read (D132): a {@code POST}, {@code PUT} or
 * {@code PATCH} with one fails, as one with a {@code Content-Type} that does not parse, or with a body and no
 * {@code Content-Type}, does; any other method's body is ignored, as Spring ignored it.</p>
 *
 * @author Runtime SDK Team
 * @since 3.4.0
 */
final class ServletRequestBody implements EndpointRequestBody {

    /** The methods whose body has to be readable; Spring ignores any other method's body it cannot read. */
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    /** The charsets that Spring's JSON converter leaves Jackson to find in the bytes; it decodes any other first. */
    private static final Set<String> FOUND_IN_THE_BYTES = Set.of(
        "UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-32", "UTF-32BE", "UTF-32LE");

    /** The content types this body is read in: what Spring's JSON converter reads. */
    static final List<MediaType> JSON_TYPES = List.of(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));

    /** A body that is not JSON text (D131): the whole body, without anything of what it held. */
    private static final List<Map<String, String>> NOT_JSON = List.of(Map.of("path", "$", "rule", "json"));

    private final HttpServletRequest request;
    private final ObjectReader host;
    private final ObjectReader exact;
    private boolean read;
    private Object value;
    private byte[] text;
    private Charset charset;

    /**
     * Creates the body of a request; nothing is read yet.
     *
     * @param request the request
     * @param json the host's mapper
     */
    ServletRequestBody(HttpServletRequest request, ObjectMapper json) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.host = Objects.requireNonNull(json, "json must not be null").readerFor(Object.class);
        this.exact = EndpointNumbers.exactReader(json);
    }

    @Override
    public Object value() {
        if (!read) {
            value = readValue();
            read = true;
        }
        return value;
    }

    @Override
    public JsonNode exact() {
        if (value() == null || text == null) {
            return null;
        }
        try {
            return FOUND_IN_THE_BYTES.contains(charset.name()) ? exact.readTree(text)
                : exact.readTree(new InputStreamReader(new ByteArrayInputStream(text), charset));
        } catch (JacksonException notJson) {
            throw new EndpointRequestInvalidException(NOT_JSON);
        }
    }

    private Object readValue() {
        String method = method(request);
        String header = request.getContentType();
        MediaType type;
        try {
            type = header == null || header.isEmpty() ? null : MediaType.parseMediaType(header);
        } catch (InvalidMediaTypeException unparsed) {
            throw new EndpointMediaTypeUnsupportedException(header, method, unparsed);
        }
        if (type != null && JSON_TYPES.stream().anyMatch(json -> json.includes(type))) {
            byte[] bytes = bytes();
            if (bytes.length == 0) {
                return null;
            }
            text = bytes;
            charset = type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
            try {
                return FOUND_IN_THE_BYTES.contains(charset.name()) ? host.readValue(bytes)
                    : host.readValue(new InputStreamReader(new ByteArrayInputStream(bytes), charset));
            } catch (JacksonException notJson) {
                throw new EndpointRequestInvalidException(NOT_JSON);
            }
        }
        if (!BODY_METHODS.contains(method)) {
            return null;
        }
        if (type == null && !hasBody()) {
            return null;
        }
        throw new EndpointMediaTypeUnsupportedException(header, method, null);
    }

    private byte[] bytes() {
        try (InputStream body = request.getInputStream()) {
            return body == null ? new byte[0] : body.readAllBytes();
        } catch (IOException unreadable) {
            throw new EndpointRequestInvalidException(NOT_JSON);
        }
    }

    private boolean hasBody() {
        try {
            InputStream body = request.getInputStream();
            return body != null && body.read() != -1;
        } catch (IOException unreadable) {
            return false;
        }
    }

    /**
     * Returns whether a request carries a form, whose parameters the servlet container would read from its body. Its
     * call ends in 415 once it has passed its access checks, so its parameters are never needed, and reading them would
     * parse the body before those checks.
     *
     * @param request the request
     * @return whether it is a {@code POST}, {@code PUT} or {@code PATCH} of a form
     */
    static boolean carriesForm(HttpServletRequest request) {
        String header = request.getContentType();
        if (header == null || !BODY_METHODS.contains(method(request))) {
            return false;
        }
        String type = header.toLowerCase(Locale.ROOT);
        return type.startsWith("application/x-www-form-urlencoded") || type.startsWith("multipart/");
    }

    /**
     * Returns the exception with which Spring refuses a content type it does not read, so that the host answers the
     * call as it answers that exception (D132).
     *
     * @param unsupported what the dispatcher failed with
     * @return Spring's exception
     */
    static HttpMediaTypeNotSupportedException unsupported(EndpointMediaTypeUnsupportedException unsupported) {
        if (unsupported.getCause() instanceof InvalidMediaTypeException unparsed) {
            return new HttpMediaTypeNotSupportedException(unparsed.getMessage(), JSON_TYPES);
        }
        MediaType type = unsupported.contentType() == null || unsupported.contentType().isEmpty()
            ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(unsupported.contentType());
        return new HttpMediaTypeNotSupportedException(type, JSON_TYPES, HttpMethod.valueOf(unsupported.method()));
    }

    private static String method(HttpServletRequest request) {
        String method = request.getMethod();
        return method == null ? "" : method.toUpperCase(Locale.ROOT);
    }
}
