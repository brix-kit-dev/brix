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
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Keeps the JSON text of a request to the {@link RuntimeShellEndpointController}, so that a typed endpoint reads its
 * numbers with every decimal digit the client wrote (root BRX-RS-MANIFEST-022; standard-link plan queue item 7, D123).
 *
 * <p>Spring reads the body as it always has, with the host's mapper: the content types it takes, its answer to a body
 * it cannot read, and what a handler of the old shape gets ({@code Double} for a decimal) stay as they were. When the
 * host's JSON converter reads the body, this advice first keeps its bytes, and hands the controller a {@link JsonBody}
 * that reads them again into a tree with {@code USE_BIG_DECIMAL_FOR_FLOATS}, only when a typed handler needs it.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@ControllerAdvice(assignableTypes = RuntimeShellEndpointController.class)
public class EndpointJsonBodyAdvice extends RequestBodyAdviceAdapter {

    /** The charsets that Spring's JSON converter leaves Jackson to find in the bytes; it decodes any other first. */
    private static final Set<String> FOUND_IN_THE_BYTES = Set.of(
        "UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-32", "UTF-32BE", "UTF-32LE");

    private final ObjectReader exact;

    /**
     * Creates the advice.
     *
     * @param json the host's mapper, which its JSON converter reads with
     */
    public EndpointJsonBodyAdvice(ObjectMapper json) {
        this.exact = EndpointNumbers.exactReader(Objects.requireNonNull(json, "json must not be null"));
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return RuntimeShellEndpointController.class.isAssignableFrom(parameter.getContainingClass())
            && JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage message, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        return new Kept(message.getBody().readAllBytes(), message.getHeaders());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage message, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (body == null || !(message instanceof Kept kept)) {
            return body;
        }
        return new JsonBody(body, () -> read(kept));
    }

    /** Reads the kept text as Spring's JSON converter reads a body, keeping every decimal digit. */
    private JsonNode read(Kept kept) {
        MediaType type = kept.headers().getContentType();
        Charset charset = type != null && type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
        if (FOUND_IN_THE_BYTES.contains(charset.name())) {
            return exact.readTree(kept.bytes());
        }
        return exact.readTree(new InputStreamReader(new ByteArrayInputStream(kept.bytes()), charset));
    }

    /**
     * A JSON request body.
     *
     * @param value the body as the host's mapper read it, which a handler of the old shape gets
     * @param exact reads the same text into a tree that keeps every decimal digit, which a typed handler's request is
     *     built from
     */
    record JsonBody(Object value, Supplier<JsonNode> exact) {
    }

    /** The bytes of a body, which the converter reads as it would have read the request. */
    private record Kept(byte[] bytes, HttpHeaders headers) implements HttpInputMessage {

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
